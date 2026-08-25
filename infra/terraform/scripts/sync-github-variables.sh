#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  infra/terraform/scripts/sync-github-variables.sh [--env dev|live|all] [--repo OWNER/REPO] [--dry-run]

Reads Terraform outputs from infra/terraform/envs/dev and/or envs/live, then
sets the GitHub repository variables consumed by .github/workflows/deploy-aws.yml.

Defaults:
  --env   all
  --repo 100Thieves-team/moimyeon-backend
EOF
}

ENVIRONMENT="all"
REPOSITORY="100Thieves-team/moimyeon-backend"
DRY_RUN=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)
      ENVIRONMENT="$2"
      shift 2
      ;;
    --repo)
      REPOSITORY="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

case "${ENVIRONMENT}" in
  dev|live|all) ;;
  *)
    echo "--env must be dev, live, or all." >&2
    exit 1
    ;;
esac

command -v terraform >/dev/null 2>&1 || {
  echo "terraform is required." >&2
  exit 1
}

command -v gh >/dev/null 2>&1 || {
  echo "GitHub CLI gh is required." >&2
  exit 1
}

if [[ "${DRY_RUN}" == "false" ]]; then
  gh auth status >/dev/null
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TERRAFORM_COMMAND="${SCRIPT_DIR}/terraform-command.sh"

terraform_output() {
  local env_name="$1"
  local output_name="$2"
  bash "${TERRAFORM_COMMAND}" output-raw "${env_name}" "${output_name}"
}

set_variable() {
  local name="$1"
  local value="$2"

  if [[ -z "${value}" ]]; then
    echo "Refusing to set empty variable ${name}." >&2
    exit 1
  fi

  if [[ "${DRY_RUN}" == "true" ]]; then
    echo "DRY RUN ${name}=${value}"
  else
    gh variable set "${name}" --body "${value}" --repo "${REPOSITORY}"
  fi
}

sync_environment() {
  local env_name="$1"
  local suffix
  suffix="$(printf '%s' "${env_name}" | tr '[:lower:]' '[:upper:]')"

  echo "Syncing ${env_name} Terraform outputs to ${REPOSITORY} GitHub variables."

  set_variable "MOIMYEON_AWS_REGION_${suffix}" "$(terraform_output "${env_name}" "aws_region")"
  set_variable "MOIMYEON_AWS_ROLE_TO_ASSUME_${suffix}" "$(terraform_output "${env_name}" "github_deploy_role_arn")"
  set_variable "MOIMYEON_AWS_ROLLBACK_ROLE_TO_ASSUME_${suffix}" "$(terraform_output "${env_name}" "github_deploy_role_arn")"
  if [[ "${env_name}" == "live" ]]; then
    set_variable "MOIMYEON_AWS_PROMOTE_ROLE_TO_ASSUME_LIVE" "$(terraform_output "${env_name}" "github_deploy_role_arn")"
  fi
  set_variable "MOIMYEON_ECR_REPOSITORY_URL_${suffix}" "$(terraform_output "${env_name}" "ecr_repository_url")"
  set_variable "MOIMYEON_ECS_CLUSTER_${suffix}" "$(terraform_output "${env_name}" "ecs_cluster_name")"
  set_variable "MOIMYEON_ECS_SERVICE_${suffix}" "$(terraform_output "${env_name}" "ecs_service_name")"
  set_variable "MOIMYEON_ECS_CONTAINER_NAME_${suffix}" "$(terraform_output "${env_name}" "ecs_container_name")"
  set_variable "MOIMYEON_IMAGE_URI_PARAMETER_${suffix}" "$(terraform_output "${env_name}" "image_uri_parameter_name")"
  set_variable "MOIMYEON_APP_URL_${suffix}" "$(terraform_output "${env_name}" "app_url")"
  set_variable "MOIMYEON_DEPLOYMENT_BUNDLE_PARAMETER_PREFIX_${suffix}" "$(terraform_output "${env_name}" "deployment_bundle_parameter_prefix")"
  set_variable "MOIMYEON_WORKER_ECR_REPOSITORY_URL_${suffix}" "$(terraform_output "${env_name}" "notification_worker_ecr_repository_url")"
  set_variable "MOIMYEON_WORKER_ECS_SERVICE_${suffix}" "$(terraform_output "${env_name}" "notification_worker_ecs_service_name")"
  set_variable "MOIMYEON_WORKER_ECS_CONTAINER_NAME_${suffix}" "$(terraform_output "${env_name}" "notification_worker_ecs_container_name")"
  set_variable "MOIMYEON_WORKER_IMAGE_URI_PARAMETER_${suffix}" "$(terraform_output "${env_name}" "notification_worker_image_uri_parameter_name")"
  set_variable "MOIMYEON_ECS_TASK_DEFINITION_${suffix}" "$(terraform_output "${env_name}" "ecs_task_definition_arn")"
  set_variable "MOIMYEON_WORKER_ECS_TASK_DEFINITION_${suffix}" "$(terraform_output "${env_name}" "notification_worker_task_definition_arn")"
}

case "${ENVIRONMENT}" in
  dev)
    sync_environment "dev"
    ;;
  live)
    sync_environment "live"
    ;;
  all)
    sync_environment "dev"
    sync_environment "live"
    ;;
esac
