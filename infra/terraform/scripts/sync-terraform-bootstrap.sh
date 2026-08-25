#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  infra/terraform/scripts/sync-terraform-bootstrap.sh \
    --phase environments --reviewer GITHUB_LOGIN [--repo OWNER/REPO] [--apply]
  infra/terraform/scripts/sync-terraform-bootstrap.sh \
    --phase variables [--repo OWNER/REPO] [--apply]

The environments phase runs before the first shared apply, closing OIDC trust
subjects with reviewer/branch policies before the AWS roles exist. The variables
phase runs after apply and reads the shared Terraform outputs.

The default is a read-only dry run. --apply performs GitHub mutations.
--apply requires an explicit --phase; the two phases cannot be combined during
mutation because that would leave an unprotected OIDC window.
It always leaves MOIMYEON_TERRAFORM_CI_ENABLED=false; activation is a separate
operator decision after the bootstrap preflight.

This script never reads or writes MOIMYEON_TERRAFORM_VARIABLE_SYNC_TOKEN.
Add that secret manually to dev-infra and live-infra with a short-lived,
Variables-write-only credential (or a GitHub App installation token).
EOF
}

REPOSITORY="100Thieves-team/moimyeon-backend"
REVIEWER=""
APPLY=false
PHASE="all"
PHASE_EXPLICIT=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)
      REPOSITORY="$2"
      shift 2
      ;;
    --reviewer)
      REVIEWER="$2"
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
  echo "--apply requires one explicit --phase to preserve the OIDC bootstrap ordering." >&2
  exit 1
fi

if [[ ("${PHASE}" == "environments" || "${PHASE}" == "all") && -z "${REVIEWER}" ]]; then
  echo "--reviewer is required for Terraform review/apply approval boundaries." >&2
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

if [[ "${PHASE}" == "variables" || "${PHASE}" == "all" ]]; then
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

if [[ "${PHASE}" == "environments" || "${PHASE}" == "all" ]]; then
  if [[ "${APPLY}" == "true" ]]; then
    REVIEWER_ID="$(gh api "users/${REVIEWER}" --jq '.id')"
    if [[ -z "${REVIEWER_ID}" ]]; then
      echo "Could not resolve GitHub reviewer ${REVIEWER}." >&2
      exit 1
    fi
  else
    REVIEWER_ID="<resolved-user-id:${REVIEWER}>"
  fi
fi

urlencode() {
  jq -rn --arg value "$1" '$value | @uri'
}

configure_environment() {
  local environment_name="$1"
  local reviewer_required="$2"
  local allowed_branches_csv="$3"
  local encoded_environment
  local payload

  encoded_environment="$(urlencode "${environment_name}")"

  if [[ "${reviewer_required}" == "true" ]]; then
    if [[ "${APPLY}" == "true" ]]; then
      payload="$(jq -nc \
        --argjson reviewer_id "${REVIEWER_ID}" \
        --argjson restricted "$([[ -n "${allowed_branches_csv}" ]] && echo true || echo false)" \
        '{
          wait_timer: 0,
          prevent_self_review: true,
          reviewers: [{type: "User", id: $reviewer_id}],
          deployment_branch_policy: (
            if $restricted then
              {protected_branches: false, custom_branch_policies: true}
            else null end
          )
        }')"
    else
      payload="reviewer=${REVIEWER_ID}, prevent_self_review=true, branches=${allowed_branches_csv:-all}"
    fi
  elif [[ -n "${allowed_branches_csv}" ]]; then
    payload="$(jq -nc '{
      wait_timer: 0,
      deployment_branch_policy: {
        protected_branches: false,
        custom_branch_policies: true
      }
    }')"
  else
    payload="$(jq -nc '{wait_timer: 0, deployment_branch_policy: null}')"
  fi

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

  if [[ -z "${allowed_branches_csv}" ]]; then
    return
  fi

  local existing_policies=""
  if [[ "${APPLY}" == "true" ]]; then
    existing_policies="$(gh api \
      -H "Accept: application/vnd.github+json" \
      "repos/${REPOSITORY}/environments/${encoded_environment}/deployment-branch-policies" \
      --jq '.branch_policies[].name')"
  fi

  local branch_name
  IFS=',' read -r -a allowed_branches <<< "${allowed_branches_csv}"
  for branch_name in "${allowed_branches[@]}"; do
    if [[ "${APPLY}" == "true" ]]; then
      if ! grep -Fxq "${branch_name}" <<< "${existing_policies}"; then
        gh api \
          --method POST \
          -H "Accept: application/vnd.github+json" \
          "repos/${REPOSITORY}/environments/${encoded_environment}/deployment-branch-policies" \
          -f "name=${branch_name}" >/dev/null
      fi
    else
      echo "DRY RUN allow ${environment_name} from branch ${branch_name}"
    fi
  done

  if [[ "${APPLY}" == "true" ]]; then
    local unexpected_policies
    unexpected_policies="$(comm -23 \
      <(printf '%s\n' "${existing_policies}" | sed '/^$/d' | sort -u) \
      <(printf '%s\n' "${allowed_branches[@]}" | sort -u))"
    if [[ -n "${unexpected_policies}" ]]; then
      echo "Unexpected branch policies remain on ${environment_name}:" >&2
      echo "${unexpected_policies}" >&2
      echo "Review and remove them manually before enabling Terraform CI." >&2
      exit 1
    fi
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
  # PR code is reviewer-gated but may originate from any internal branch.
  configure_environment terraform-review-plan true ""
  # Scheduled workflow runs from the repository default branch (dev).
  configure_environment terraform-drift-plan false "dev"
  # workflow_run and its reusable calls execute from the default branch ref.
  # The workflow independently pins and validates the dev/main source SHA.
  configure_environment terraform-apply-plan false "dev"
  # Exact-plan apply is always reviewer-gated and cannot self-approve.
  configure_environment shared-infra true "dev"
  configure_environment dev-infra true "dev"
  configure_environment live-infra true "dev"
fi

if [[ "${PHASE}" == "variables" || "${PHASE}" == "all" ]]; then
  set_repository_variable MOIMYEON_TERRAFORM_PLAN_BUCKET "${PLAN_BUCKET}"
  set_repository_variable MOIMYEON_TERRAFORM_PLAN_KMS_KEY_ARN "${PLAN_KMS_KEY_ARN}"
  set_repository_variable MOIMYEON_TERRAFORM_CI_ENABLED false

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
  1. Add MOIMYEON_TERRAFORM_VARIABLE_SYNC_TOKEN to dev-infra and live-infra.
  2. Verify reviewer and branch policies in GitHub Settings > Environments.
  3. Run a review plan and scheduled drift plan preflight.
  4. Set MOIMYEON_TERRAFORM_CI_ENABLED=true only after both preflights pass.
EOF
