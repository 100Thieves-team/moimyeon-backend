#!/usr/bin/env bash

set -euo pipefail

cluster=""
service=""
container=""
template_task_definition=""
existing_task_definition=""
image_uri=""
ssm_parameter=""
health_grace_seconds=""
smoke_base_url=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --cluster) cluster="$2"; shift 2 ;;
    --service) service="$2"; shift 2 ;;
    --container) container="$2"; shift 2 ;;
    --template-task-definition) template_task_definition="$2"; shift 2 ;;
    --existing-task-definition) existing_task_definition="$2"; shift 2 ;;
    --image-uri) image_uri="$2"; shift 2 ;;
    --ssm-parameter) ssm_parameter="$2"; shift 2 ;;
    --health-grace-seconds) health_grace_seconds="$2"; shift 2 ;;
    --smoke-base-url) smoke_base_url="$2"; shift 2 ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

for required_value in \
  "${cluster}" \
  "${service}" \
  "${container}" \
  "${image_uri}" \
  "${ssm_parameter}"; do
  if [ -z "${required_value}" ]; then
    echo "All required deployment arguments must be non-empty." >&2
    exit 1
  fi
done

if { [ -z "${template_task_definition}" ] && [ -z "${existing_task_definition}" ]; } \
  || { [ -n "${template_task_definition}" ] && [ -n "${existing_task_definition}" ]; }; then
  echo "Exactly one task definition source is required: template or existing." >&2
  exit 1
fi

if [[ ! "${image_uri}" =~ @sha256:[0-9a-f]{64}$ ]]; then
  echo "Deployment image must use an immutable sha256 digest: ${image_uri}." >&2
  exit 1
fi

command -v aws >/dev/null 2>&1 || {
  echo "aws CLI is required." >&2
  exit 1
}
command -v jq >/dev/null 2>&1 || {
  echo "jq is required." >&2
  exit 1
}
command -v docker >/dev/null 2>&1 || {
  echo "docker with buildx is required." >&2
  exit 1
}

immutable_image_uri() {
  local image_ref="$1"
  local repository
  local digest

  case "${image_ref}" in
    *@sha256:*)
      repository="${image_ref%@*}"
      digest="${image_ref##*@}"
      ;;
    *)
      repository="${image_ref%:*}"
      digest="$(docker buildx imagetools inspect \
        "${image_ref}" --format '{{.Manifest.Digest}}')"
      ;;
  esac
  echo "${repository}@${digest}"
}

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
work_dir="$(mktemp -d /tmp/moimyeon-ecs-deploy.XXXXXX)"
trap 'rm -rf "${work_dir}"' EXIT

aws ecs describe-services \
  --cluster "${cluster}" \
  --services "${service}" \
  > "${work_dir}/service-before.json"
previous_task_definition="$(jq -r \
  '.services[0].deployments[] | select(.status == "PRIMARY") | .taskDefinition' \
  "${work_dir}/service-before.json")"
if [ -z "${previous_task_definition}" ] || [ "${previous_task_definition}" = "null" ]; then
  echo "ECS service has no PRIMARY task definition: ${service}." >&2
  exit 1
fi

aws ecs describe-task-definition \
  --task-definition "${previous_task_definition}" \
  --query taskDefinition \
  > "${work_dir}/previous-task-definition.json"
previous_recorded_image="$(jq -r --arg container "${container}" \
  '.containerDefinitions[] | select(.name == $container) | .image' \
  "${work_dir}/previous-task-definition.json")"
previous_image_uri="$(immutable_image_uri "${previous_recorded_image}")"

put_ssm_with_retry() {
  local value="$1"
  local attempt

  for attempt in 1 2 3; do
    if aws ssm put-parameter \
      --name "${ssm_parameter}" \
      --type String \
      --value "${value}" \
      --overwrite >/dev/null; then
      return 0
    fi
    if [ "${attempt}" -lt 3 ]; then
      sleep $((attempt * 2))
    fi
  done
  return 1
}

restore_previous_state() {
  local restored_task_definition
  local rollback_args=(
    --cluster "${cluster}"
    --service "${service}"
    --task-definition "${previous_task_definition}"
    --force-new-deployment
  )
  if [ -n "${health_grace_seconds}" ]; then
    rollback_args+=(--health-check-grace-period-seconds "${health_grace_seconds}")
  fi

  aws ecs update-service "${rollback_args[@]}" > "${work_dir}/rollback-update.json"
  aws ecs wait services-stable --cluster "${cluster}" --services "${service}"
  restored_task_definition="$(aws ecs describe-services \
    --cluster "${cluster}" \
    --services "${service}" \
    --query 'services[0].deployments[?status==`PRIMARY`].taskDefinition | [0]' \
    --output text)"
  if [ "${restored_task_definition}" != "${previous_task_definition}" ]; then
    echo "Automatic rollback failed: expected ${previous_task_definition}, got ${restored_task_definition}." >&2
    return 1
  fi
  if ! put_ssm_with_retry "${previous_image_uri}"; then
    echo "Previous ECS revision was restored, but SSM restoration failed." >&2
    return 1
  fi
}

task_definition_source="${template_task_definition:-${existing_task_definition}}"

aws ecs describe-task-definition \
  --task-definition "${task_definition_source}" \
  --query taskDefinition \
  > "${work_dir}/task-definition.json"

if ! jq -e --arg container "${container}" \
  '.containerDefinitions | any(.name == $container)' \
  "${work_dir}/task-definition.json" >/dev/null; then
  echo "Container ${container} is absent from task definition ${task_definition_source}." >&2
  exit 1
fi

if [ -n "${existing_task_definition}" ]; then
  recorded_image="$(jq -r --arg container "${container}" \
    '.containerDefinitions[] | select(.name == $container) | .image' \
    "${work_dir}/task-definition.json")"
  recorded_image_uri="$(immutable_image_uri "${recorded_image}")"
  if [ "${recorded_image_uri}" != "${image_uri}" ]; then
    echo "Historical task definition image does not match the selected deployment bundle." >&2
    exit 1
  fi
  next_task_definition="$(jq -r '.taskDefinitionArn' "${work_dir}/task-definition.json")"
  if [ "${next_task_definition%:*}" != "${previous_task_definition%:*}" ]; then
    echo "Historical task definition belongs to a different ECS family." >&2
    exit 1
  fi
else
  jq \
    --arg container "${container}" \
    --arg image_uri "${image_uri}" '
    .containerDefinitions |= map(
      if .name == $container then .image = $image_uri else . end
    )
    | del(
      .taskDefinitionArn,
      .revision,
      .status,
      .requiresAttributes,
      .compatibilities,
      .registeredAt,
      .registeredBy
    )
  ' "${work_dir}/task-definition.json" > "${work_dir}/task-definition-next.json"

  next_task_definition="$(aws ecs register-task-definition \
    --cli-input-json "file://${work_dir}/task-definition-next.json" \
    --query 'taskDefinition.taskDefinitionArn' \
    --output text)"
fi

update_args=(
  --cluster "${cluster}"
  --service "${service}"
  --task-definition "${next_task_definition}"
  --force-new-deployment
)
if [ -n "${health_grace_seconds}" ]; then
  update_args+=(--health-check-grace-period-seconds "${health_grace_seconds}")
fi

aws ecs update-service "${update_args[@]}" > "${work_dir}/service-update.json"
if ! aws ecs wait services-stable --cluster "${cluster}" --services "${service}"; then
  echo "ECS did not stabilize; restoring previous ECS and SSM state." >&2
  restore_previous_state
  exit 1
fi
aws ecs describe-services \
  --cluster "${cluster}" \
  --services "${service}" \
  > "${work_dir}/service.json"

primary_task_definition="$(jq -r \
  '.services[0].deployments[] | select(.status == "PRIMARY") | .taskDefinition' \
  "${work_dir}/service.json")"
desired_count="$(jq -r '.services[0].desiredCount' "${work_dir}/service.json")"
running_count="$(jq -r '.services[0].runningCount' "${work_dir}/service.json")"
pending_count="$(jq -r '.services[0].pendingCount' "${work_dir}/service.json")"

if [ "${primary_task_definition}" != "${next_task_definition}" ] \
  || [ "${running_count}" != "${desired_count}" ] \
  || [ "${pending_count}" != "0" ]; then
  echo "ECS service stabilized on an unexpected revision or task count." >&2
  jq '.services[0].deployments' "${work_dir}/service.json" >&2
  restore_previous_state
  exit 1
fi

if [ -n "${smoke_base_url}" ] && [ "${desired_count}" != "0" ]; then
  if ! bash "${script_dir}/smoke-test.sh" "${smoke_base_url}"; then
    echo "Smoke failed; restoring previous task definition ${previous_task_definition}." >&2
    restore_previous_state
    echo "Previous task definition restored after smoke failure." >&2
    exit 1
  fi
fi

if ! put_ssm_with_retry "${image_uri}"; then
  echo "SSM commit failed; restoring previous ECS and SSM state." >&2
  restore_previous_state
  exit 1
fi

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "task_definition_arn=${next_task_definition}" >> "${GITHUB_OUTPUT}"
  echo "previous_task_definition_arn=${previous_task_definition}" >> "${GITHUB_OUTPUT}"
  echo "previous_image_uri=${previous_image_uri}" >> "${GITHUB_OUTPUT}"
fi

echo "ECS service ${service} deployed ${image_uri} as ${next_task_definition}."
