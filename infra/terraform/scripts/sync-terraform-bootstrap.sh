#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  infra/terraform/scripts/sync-terraform-bootstrap.sh \
    --phase environments [--repo OWNER/REPO] [--apply]
  infra/terraform/scripts/sync-terraform-bootstrap.sh \
    --phase variables [--repo OWNER/REPO] [--apply]

The environments phase creates variable namespaces without reviewers, wait
timers, or deployment branch policies. AWS OIDC trust is enforced by immutable
repository IDs, workflow/ref claims, and reusable workflow paths instead.
The variables phase runs after apply and reads the shared Terraform outputs.

The default is a read-only dry run. --apply performs GitHub mutations.
--apply requires an explicit --phase; the variables phase depends on outputs
from the first shared apply.
It always leaves MOIMYEON_TERRAFORM_CI_ENABLED=false; activation is a separate
operator decision after the bootstrap preflight.

This script never reads or writes MOIMYEON_TERRAFORM_VARIABLE_SYNC_TOKEN.
Store that credential in the pre-created SSM SecureString named by
MOIMYEON_TERRAFORM_VARIABLE_SYNC_TOKEN_PARAMETER. GitHub receives only the
parameter name; the trusted apply OIDC role reads the value after assumption.
EOF
}

REPOSITORY="100Thieves-team/moimyeon-backend"
APPLY=false
PHASE="all"
PHASE_EXPLICIT=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)
      REPOSITORY="$2"
      shift 2
      ;;
    --phase)
      PHASE="$2"
      PHASE_EXPLICIT=true
      shift 2
      ;;
    --apply)
      APPLY=true
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

case "${PHASE}" in
  environments|variables|all) ;;
  *)
    echo "--phase must be environments or variables." >&2
    exit 1
    ;;
esac

if [[ "${APPLY}" == "true" && ("${PHASE_EXPLICIT}" != "true" || "${PHASE}" == "all") ]]; then
  echo "--apply requires one explicit bootstrap phase." >&2
  exit 1
fi

if [[ ! "${REPOSITORY}" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]]; then
  echo "--repo must be OWNER/REPO." >&2
  exit 1
fi

required_commands=(gh jq)
if [[ "${PHASE}" == "variables" || "${PHASE}" == "all" ]]; then
  required_commands+=(terraform)
fi
for command_name in "${required_commands[@]}"; do
  command -v "${command_name}" >/dev/null 2>&1 || {
    echo "${command_name} is required." >&2
    exit 1
  }
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TERRAFORM_COMMAND="${SCRIPT_DIR}/terraform-command.sh"

terraform_output_raw() {
  bash "${TERRAFORM_COMMAND}" output-raw shared "$1"
}

terraform_apply_role_arn() {
  bash "${TERRAFORM_COMMAND}" output-json shared terraform_apply_role_arns \
    | jq -er --arg environment "$1" '.[$environment]'
}

assert_dev_state_forget_complete() {
  local state_addresses
  local secret_resource
  state_addresses="$(bash "${TERRAFORM_COMMAND}" state-list dev)"

  for secret_resource in \
    module.dev.aws_ssm_parameter.jwt_secret \
    module.dev.aws_ssm_parameter.oauth_google_client_secret \
    module.dev.random_password.jwt; do
    if grep -Fxq "${secret_resource}" <<< "${state_addresses}"; then
      echo "Refusing to expose Terraform plan role Variables while ${secret_resource} remains in current dev state." >&2
      echo "Apply the reviewed dev forget plan with a human AWS identity, then rerun this phase." >&2
      exit 1
    fi
  done
}

if [[ "${PHASE}" == "variables" || "${PHASE}" == "all" ]]; then
  assert_dev_state_forget_complete

  PLAN_BUCKET="$(terraform_output_raw terraform_plan_artifact_bucket_name)"
  PLAN_KMS_KEY_ARN="$(terraform_output_raw terraform_plan_kms_key_arn)"
  REVIEW_PLAN_ROLE_ARN="$(terraform_output_raw terraform_review_plan_role_arn)"
  DRIFT_PLAN_ROLE_ARN="$(terraform_output_raw terraform_drift_plan_role_arn)"
  APPLY_PLAN_ROLE_ARN="$(terraform_output_raw terraform_apply_plan_role_arn)"
  SHARED_APPLY_ROLE_ARN="$(terraform_apply_role_arn shared)"
  DEV_APPLY_ROLE_ARN="$(terraform_apply_role_arn dev)"
  LIVE_APPLY_ROLE_ARN="$(terraform_apply_role_arn live)"

  for value in \
    "${PLAN_BUCKET}" \
    "${PLAN_KMS_KEY_ARN}" \
    "${REVIEW_PLAN_ROLE_ARN}" \
    "${DRIFT_PLAN_ROLE_ARN}" \
    "${APPLY_PLAN_ROLE_ARN}" \
    "${SHARED_APPLY_ROLE_ARN}" \
    "${DEV_APPLY_ROLE_ARN}" \
    "${LIVE_APPLY_ROLE_ARN}"; do
    if [[ -z "${value}" || "${value}" == "null" ]]; then
      echo "Shared Terraform bootstrap outputs are incomplete. Apply the reviewed shared plan first." >&2
      exit 1
    fi
  done
fi

if [[ "${APPLY}" == "true" ]]; then
  gh auth status >/dev/null
fi

urlencode() {
  jq -rn --arg value "$1" '$value | @uri'
}

configure_environment() {
  local environment_name="$1"
  local encoded_environment
  local payload

  encoded_environment="$(urlencode "${environment_name}")"
  payload="$(jq -nc '{
    wait_timer: 0,
    prevent_self_review: false,
    reviewers: [],
    deployment_branch_policy: null
  }')"

  if [[ "${APPLY}" == "true" ]]; then
    printf '%s' "${payload}" \
      | gh api \
        --method PUT \
        -H "Accept: application/vnd.github+json" \
        "repos/${REPOSITORY}/environments/${encoded_environment}" \
        --input - >/dev/null
  else
    echo "DRY RUN environment ${environment_name}: ${payload}"
  fi
}

set_repository_variable() {
  local name="$1"
  local value="$2"

  if [[ "${APPLY}" == "true" ]]; then
    gh variable set "${name}" --repo "${REPOSITORY}" --body "${value}"
  else
    echo "DRY RUN repository variable ${name}=${value}"
  fi
}

set_environment_variable() {
  local environment_name="$1"
  local name="$2"
  local value="$3"

  if [[ "${APPLY}" == "true" ]]; then
    gh variable set "${name}" \
      --env "${environment_name}" \
      --repo "${REPOSITORY}" \
      --body "${value}"
  else
    echo "DRY RUN ${environment_name} variable ${name}=${value}"
  fi
}

if [[ "${PHASE}" == "environments" || "${PHASE}" == "all" ]]; then
  configure_environment terraform-review-plan
  configure_environment terraform-drift-plan
  configure_environment terraform-apply-plan
  configure_environment shared-infra
  configure_environment dev-infra
  configure_environment live-infra
fi

if [[ "${PHASE}" == "variables" || "${PHASE}" == "all" ]]; then
  set_repository_variable MOIMYEON_TERRAFORM_PLAN_BUCKET "${PLAN_BUCKET}"
  set_repository_variable MOIMYEON_TERRAFORM_PLAN_KMS_KEY_ARN "${PLAN_KMS_KEY_ARN}"
  set_repository_variable MOIMYEON_TERRAFORM_VARIABLE_SYNC_TOKEN_PARAMETER "/moimyeon/shared/terraform/GITHUB_VARIABLE_SYNC_TOKEN"
  set_repository_variable MOIMYEON_TERRAFORM_CI_ENABLED false
  set_repository_variable MOIMYEON_TERRAFORM_LIVE_CI_ENABLED false
  set_repository_variable MOIMYEON_LIVE_DEPLOY_ENABLED false
  set_repository_variable MOIMYEON_LIVE_ROLLBACK_ENABLED false

  set_environment_variable terraform-review-plan MOIMYEON_TERRAFORM_PLAN_ROLE_TO_ASSUME "${REVIEW_PLAN_ROLE_ARN}"
  set_environment_variable terraform-drift-plan MOIMYEON_TERRAFORM_PLAN_ROLE_TO_ASSUME "${DRIFT_PLAN_ROLE_ARN}"
  set_environment_variable terraform-apply-plan MOIMYEON_TERRAFORM_APPLY_PLAN_ROLE_TO_ASSUME "${APPLY_PLAN_ROLE_ARN}"
  set_environment_variable shared-infra MOIMYEON_TERRAFORM_APPLY_ROLE_TO_ASSUME "${SHARED_APPLY_ROLE_ARN}"
  set_environment_variable dev-infra MOIMYEON_TERRAFORM_APPLY_ROLE_TO_ASSUME "${DEV_APPLY_ROLE_ARN}"
  set_environment_variable live-infra MOIMYEON_TERRAFORM_APPLY_ROLE_TO_ASSUME "${LIVE_APPLY_ROLE_ARN}"
fi

if [[ "${APPLY}" == "true" ]]; then
  result_message="was reconciled"
else
  result_message="dry run completed"
fi

cat <<EOF

Terraform CI bootstrap GitHub ${PHASE} phase ${result_message}.

Still manual:
  1. Create /moimyeon/shared/terraform/GITHUB_VARIABLE_SYNC_TOKEN as an SSM SecureString.
  2. Verify all six Terraform Environments have no protection rules.
  3. Run a review plan and scheduled drift plan preflight.
  4. Set MOIMYEON_TERRAFORM_CI_ENABLED=true only after both preflights pass.
EOF
