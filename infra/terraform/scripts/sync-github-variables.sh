#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  infra/terraform/scripts/sync-github-variables.sh [--env dev|live|all] [--repo OWNER/REPO] [--applied-sha SHA] [--dry-run]

Reads Terraform outputs from infra/terraform/envs/dev and/or envs/live, then
sets the GitHub repository variables consumed by .github/workflows/deploy-aws.yml.

--applied-sha records the dev source SHA whose Terraform state is now current
(MOIMYEON_TERRAFORM_APPLIED_SHA_DEV). Terraform Apply skips later commits that
leave infra/terraform unchanged since that SHA.

Defaults:
  --env   all
  --repo 100Thieves-team/moimyeon-backend
EOF
}

ENVIRONMENT="all"
REPOSITORY="100Thieves-team/moimyeon-backend"
DRY_RUN=false
APPLIED_SHA=""

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
    --applied-sha)
      APPLIED_SHA="$2"
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

if [[ -n "${APPLIED_SHA}" ]]; then
  if [[ "${ENVIRONMENT}" != "dev" ]] || [[ ! "${APPLIED_SHA}" =~ ^[0-9a-f]{40}$ ]]; then
    echo "--applied-sha requires --env dev and a full commit SHA." >&2
    exit 1
  fi
fi

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

OUTPUTS_FILE="$(mktemp)"
trap 'rm -f "${OUTPUTS_FILE}"' EXIT

# Read every needed output with a single terraform init; one init per output
# made this sync take several minutes.
load_terraform_outputs() {
  local env_name="$1"
  local output_names=(
    aws_region
    github_deploy_role_arn
    ecr_repository_url
    ecs_cluster_name
    ecs_service_name
    ecs_container_name
    image_uri_parameter_name
    app_url
    deployment_bundle_parameter_prefix
    notification_worker_ecr_repository_url
    notification_worker_ecs_service_name
    notification_worker_ecs_container_name
    notification_worker_image_uri_parameter_name
    ecs_task_definition_arn
    notification_worker_task_definition_arn
  )
  if [[ "${env_name}" == "dev" ]]; then
    output_names+=(
      github_pr_image_role_arn
      pr_image_candidate_repository_url
      pr_image_worker_candidate_repository_url
      deploy_config_parameter_name
      terraform_applied_sha_parameter_name
    )
  fi
  bash "${TERRAFORM_COMMAND}" output-raw-many "${env_name}" "${output_names[@]}" > "${OUTPUTS_FILE}"
}

terraform_output() {
  local output_name="$1"
  local line
  line="$(grep -m1 "^${output_name}=" "${OUTPUTS_FILE}" || true)"
  if [[ -z "${line}" ]]; then
    echo "Terraform output ${output_name} was not loaded." >&2
    exit 1
  fi
  printf '%s' "${line#*=}"
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
    echo "Setting GitHub variable ${name}."
    if ! gh variable set "${name}" --body "${value}" --repo "${REPOSITORY}"; then
      echo "::error::Failed to set GitHub variable ${name}; rerun the resumable sync job." >&2
      return 1
    fi
  fi
}

sync_environment() {
  local env_name="$1"
  local suffix
  suffix="$(printf '%s' "${env_name}" | tr '[:lower:]' '[:upper:]')"

  echo "Syncing ${env_name} Terraform outputs to ${REPOSITORY} GitHub variables."
  load_terraform_outputs "${env_name}"

  set_variable "MOIMYEON_AWS_REGION_${suffix}" "$(terraform_output "aws_region")"
  set_variable "MOIMYEON_AWS_ROLE_TO_ASSUME_${suffix}" "$(terraform_output "github_deploy_role_arn")"
  set_variable "MOIMYEON_AWS_ROLLBACK_ROLE_TO_ASSUME_${suffix}" "$(terraform_output "github_deploy_role_arn")"
  if [[ "${env_name}" == "live" ]]; then
    set_variable "MOIMYEON_AWS_PROMOTE_ROLE_TO_ASSUME_LIVE" "$(terraform_output "github_deploy_role_arn")"
  fi
  set_variable "MOIMYEON_ECR_REPOSITORY_URL_${suffix}" "$(terraform_output "ecr_repository_url")"
  set_variable "MOIMYEON_ECS_CLUSTER_${suffix}" "$(terraform_output "ecs_cluster_name")"
  set_variable "MOIMYEON_ECS_SERVICE_${suffix}" "$(terraform_output "ecs_service_name")"
  set_variable "MOIMYEON_ECS_CONTAINER_NAME_${suffix}" "$(terraform_output "ecs_container_name")"
  set_variable "MOIMYEON_IMAGE_URI_PARAMETER_${suffix}" "$(terraform_output "image_uri_parameter_name")"
  set_variable "MOIMYEON_APP_URL_${suffix}" "$(terraform_output "app_url")"
  set_variable "MOIMYEON_DEPLOYMENT_BUNDLE_PARAMETER_PREFIX_${suffix}" "$(terraform_output "deployment_bundle_parameter_prefix")"
  set_variable "MOIMYEON_WORKER_ECR_REPOSITORY_URL_${suffix}" "$(terraform_output "notification_worker_ecr_repository_url")"
  set_variable "MOIMYEON_WORKER_ECS_SERVICE_${suffix}" "$(terraform_output "notification_worker_ecs_service_name")"
  set_variable "MOIMYEON_WORKER_ECS_CONTAINER_NAME_${suffix}" "$(terraform_output "notification_worker_ecs_container_name")"
  set_variable "MOIMYEON_WORKER_IMAGE_URI_PARAMETER_${suffix}" "$(terraform_output "notification_worker_image_uri_parameter_name")"
  set_variable "MOIMYEON_ECS_TASK_DEFINITION_${suffix}" "$(terraform_output "ecs_task_definition_arn")"
  set_variable "MOIMYEON_WORKER_ECS_TASK_DEFINITION_${suffix}" "$(terraform_output "notification_worker_task_definition_arn")"
  if [[ "${env_name}" == "dev" ]]; then
    set_variable "MOIMYEON_PR_IMAGE_ROLE_TO_ASSUME_DEV" "$(terraform_output "github_pr_image_role_arn")"
    set_variable "MOIMYEON_PR_IMAGE_CANDIDATE_REPOSITORY_URL_DEV" "$(terraform_output "pr_image_candidate_repository_url")"
    set_variable "MOIMYEON_PR_IMAGE_WORKER_CANDIDATE_REPOSITORY_URL_DEV" "$(terraform_output "pr_image_worker_candidate_repository_url")"
    set_variable "MOIMYEON_DEPLOY_CONFIG_PARAMETER_DEV" "$(terraform_output "deploy_config_parameter_name")"
    set_variable "MOIMYEON_TERRAFORM_APPLIED_SHA_PARAMETER_DEV" "$(terraform_output "terraform_applied_sha_parameter_name")"
    if [[ -n "${APPLIED_SHA}" ]]; then
      set_variable "MOIMYEON_TERRAFORM_APPLIED_SHA_DEV" "${APPLIED_SHA}"
    fi
  fi
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
