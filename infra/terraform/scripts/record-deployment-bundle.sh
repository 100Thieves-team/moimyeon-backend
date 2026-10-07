#!/usr/bin/env bash

set -euo pipefail

parameter_prefix="${1:?deployment bundle parameter prefix is required}"
environment="${2:?environment is required}"
source_sha="${3:?source SHA is required}"
api_image_uri="${4:?Core API image URI is required}"
api_task_definition_arn="${5:?Core API task definition ARN is required}"
worker_image_uri="${6:-}"
worker_task_definition_arn="${7:-}"

case "${environment}" in dev|live) ;; *) exit 1 ;; esac
[[ "${source_sha}" =~ ^[0-9a-f]{40}$ ]] || exit 1
[[ "${api_image_uri}" =~ @sha256:[0-9a-f]{64}$ ]] || exit 1
[[ "${api_task_definition_arn}" =~ ^arn:aws[a-zA-Z-]*:ecs:[a-z0-9-]+:[0-9]{12}:task-definition/[A-Za-z0-9_-]+:[0-9]+$ ]] || exit 1

if { [ -z "${worker_image_uri}" ] && [ -n "${worker_task_definition_arn}" ]; } \
  || { [ -n "${worker_image_uri}" ] && [ -z "${worker_task_definition_arn}" ]; }; then
  echo "Worker bundle fields must be both present or both absent." >&2
  exit 1
fi
if [ -n "${worker_image_uri}" ]; then
  [[ "${worker_image_uri}" =~ @sha256:[0-9a-f]{64}$ ]] || exit 1
  [[ "${worker_task_definition_arn}" =~ ^arn:aws[a-zA-Z-]*:ecs:[a-z0-9-]+:[0-9]{12}:task-definition/[A-Za-z0-9_-]+:[0-9]+$ ]] || exit 1
fi

parameter_name="${parameter_prefix%/}/${source_sha:0:12}"
manifest="$(jq -cn \
  --arg environment "${environment}" \
  --arg source_sha "${source_sha}" \
  --arg api_image_uri "${api_image_uri}" \
  --arg api_task_definition_arn "${api_task_definition_arn}" \
  --arg worker_image_uri "${worker_image_uri}" \
  --arg worker_task_definition_arn "${worker_task_definition_arn}" '
  {
    environment: $environment,
    sourceSha: $source_sha,
    api: {
      imageUri: $api_image_uri,
      taskDefinitionArn: $api_task_definition_arn
    },
    worker: (
      if $worker_image_uri == "" then null
      else {
        imageUri: $worker_image_uri,
        taskDefinitionArn: $worker_task_definition_arn
      }
      end
    )
  }')"

for attempt in 1 2 3; do
  existing_manifest="$(aws ssm get-parameter \
    --name "${parameter_name}" \
    --query 'Parameter.Value' \
    --output text 2>/dev/null || true)"
  if [ -n "${existing_manifest}" ]; then
    if [ "${existing_manifest}" != "${manifest}" ]; then
      echo "Deployment bundle already exists with different immutable content: ${parameter_name}." >&2
      exit 1
    fi
    echo "Deployment bundle already recorded: ${parameter_name}."
    exit 0
  fi

  if aws ssm put-parameter \
    --name "${parameter_name}" \
    --type String \
    --value "${manifest}" >/dev/null 2>&1; then
    echo "Deployment bundle recorded: ${parameter_name}."
    exit 0
  fi
  if [ "${attempt}" -lt 3 ]; then
    sleep $((attempt * 2))
  fi
done

echo "Failed to record deployment bundle: ${parameter_name}." >&2
exit 1
