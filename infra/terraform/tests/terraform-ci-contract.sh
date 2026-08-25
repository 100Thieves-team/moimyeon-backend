#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PLAN_WORKFLOW="${ROOT_DIR}/.github/workflows/terraform-plan.yml"
APPLY_WORKFLOW="${ROOT_DIR}/.github/workflows/terraform-apply.yml"
TRUSTED_PLAN_WORKFLOW="${ROOT_DIR}/.github/workflows/terraform-plan-environment.yml"
ENV_APPLY_WORKFLOW="${ROOT_DIR}/.github/workflows/terraform-apply-environment.yml"
COMMAND_SCRIPT="${ROOT_DIR}/infra/terraform/scripts/terraform-command.sh"

fail() {
  echo "Terraform CI 계약 위반: $1" >&2
  exit 1
}

assert_contains() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  grep -Eq -- "${pattern}" "${file}" || fail "${message}"
}

assert_not_contains() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if grep -Eq -- "${pattern}" "${file}"; then
    fail "${message}"
  fi
}

[ -f "${PLAN_WORKFLOW}" ] || fail "PR·drift plan workflow가 없다."
[ -f "${APPLY_WORKFLOW}" ] || fail "merged-SHA exact plan apply workflow가 없다."
[ -f "${TRUSTED_PLAN_WORKFLOW}" ] || fail "trusted merged-SHA plan reusable workflow가 없다."
[ -f "${ENV_APPLY_WORKFLOW}" ] || fail "environment apply reusable workflow가 없다."

assert_contains "${COMMAND_SCRIPT}" '-var-file="\$\{environment\}\.tfvars"' "공식 plan은 committed 환경 tfvars를 명시해야 한다."
assert_contains "${COMMAND_SCRIPT}" 'TF_VAR_\*\|TF_CLI_ARGS.*TF_WORKSPACE\|TF_DATA_DIR' "공식 plan은 변수·workspace·data-dir implicit override를 거부해야 한다."
assert_contains "${COMMAND_SCRIPT}" 'workspace select default' "공식 init·plan은 default workspace로 고정해야 한다."
assert_not_contains "${COMMAND_SCRIPT}" 'terraform[^[:space:]]*[[:space:]]+apply' "로컬 공식 command script에 apply를 노출하면 안 된다."

assert_contains "${PLAN_WORKFLOW}" '^  pull_request:' "Terraform PR plan이 필요하다."
assert_contains "${PLAN_WORKFLOW}" 'terraform-plan-environment\.yml' "reusable plan workflow 변경도 PR plan을 깨워야 한다."
assert_contains "${PLAN_WORKFLOW}" 'terraform-apply-environment\.yml' "reusable apply workflow 변경도 PR plan을 깨워야 한다."
assert_contains "${PLAN_WORKFLOW}" '^  schedule:' "Terraform drift plan이 필요하다."
assert_contains "${PLAN_WORKFLOW}" 'id-token:[[:space:]]*write' "plan은 OIDC를 사용해야 한다."
assert_contains "${PLAN_WORKFLOW}" 'terraform-review-plan' "PR-authored Terraform이 state를 읽기 전에 review-plan Environment 승인이 필요하다."
assert_contains "${PLAN_WORKFLOW}" 'terraform-drift-plan' "scheduled drift는 PR approval과 분리된 trusted Environment를 사용해야 한다."
assert_contains "${PLAN_WORKFLOW}" 'head\.repo\.full_name == github\.repository' "fork PR은 AWS plan 권한을 얻으면 안 된다."
assert_contains "${PLAN_WORKFLOW}" 'MOIMYEON_TERRAFORM_CI_ENABLED == '\''true'\''' "bootstrap 전 Terraform AWS plan은 fail-closed여야 한다."
assert_contains "${PLAN_WORKFLOW}" 'terraform-command\.sh plan' "PR과 drift는 공식 plan command를 사용해야 한다."
assert_contains "${PLAN_WORKFLOW}" 'aws s3api put-object' "raw plan은 private S3에만 저장해야 한다."
assert_contains "${PLAN_WORKFLOW}" 'MOIMYEON_TERRAFORM_PLAN_ROLE_TO_ASSUME' "review/drift environment는 각자 scoped plan role을 제공해야 한다."
assert_contains "${PLAN_WORKFLOW}" '--server-side-encryption[[:space:]]+aws:kms' "raw plan artifact는 KMS로 암호화해야 한다."
assert_contains "${PLAN_WORKFLOW}" '--if-none-match[[:space:]]+'\''\*'\''' "plan artifact key는 create-only여야 한다."
assert_not_contains "${PLAN_WORKFLOW}" 'actions/upload-artifact' "raw plan을 GitHub artifact로 올리면 안 된다."
assert_not_contains "${PLAN_WORKFLOW}" 'workflow_dispatch' "임의 ref에서 privileged review plan을 시작하면 안 된다."

assert_contains "${APPLY_WORKFLOW}" '^  workflow_run:' "Terraform apply는 CI 성공 workflow_run 뒤에서만 시작해야 한다."
assert_contains "${APPLY_WORKFLOW}" 'workflows:[[:space:]]*\[[[:space:]]*CI[[:space:]]*\]' "Terraform apply는 CI workflow에 종속해야 한다."
assert_contains "${APPLY_WORKFLOW}" 'workflow_run\.conclusion == '\''success'\''' "CI 실패 revision은 Terraform apply 후보가 될 수 없다."
assert_contains "${APPLY_WORKFLOW}" 'github\.event\.workflow_run\.head_sha' "Terraform plan/apply는 CI가 검증한 정확한 SHA를 사용해야 한다."
assert_contains "${APPLY_WORKFLOW}" 'run-name:[[:space:]]*Terraform Apply' "app workflow가 정확한 Terraform boundary run을 식별할 수 있어야 한다."
assert_contains "${APPLY_WORKFLOW}" 'Pin CI-successful trigger revision' "Terraform Apply run-name과 실제 source SHA를 고정해야 한다."
assert_not_contains "${APPLY_WORKFLOW}" 'Detect Terraform changes' "trigger commit 단건 diff gate는 선행 Terraform 변경을 영구 누락시킬 수 있다."
assert_contains "${APPLY_WORKFLOW}" 'terraform-plan-environment\.yml' "merged reusable plan 변경도 apply pipeline을 깨워야 한다."
assert_contains "${APPLY_WORKFLOW}" 'terraform-apply-environment\.yml' "merged reusable apply 변경도 apply pipeline을 깨워야 한다."
assert_contains "${APPLY_WORKFLOW}" 'MOIMYEON_TERRAFORM_CI_ENABLED == '\''true'\''' "bootstrap 전 Terraform apply는 fail-closed여야 한다."
assert_contains "${APPLY_WORKFLOW}" 'Application deployment remains frozen' "Terraform CI 비활성 상태를 app-ready success로 보고하면 안 된다."
assert_contains "${APPLY_WORKFLOW}" 'branches:[[:space:]]*\[[[:space:]]*dev,[[:space:]]*main[[:space:]]*\]' "apply는 dev/main merged SHA만 허용해야 한다."
assert_not_contains "${APPLY_WORKFLOW}" 'MOIMYEON_TERRAFORM_REVIEW_PLAN_ROLE_TO_ASSUME' "PR plan role이 apply artifact prefix를 쓰면 안 된다."
assert_contains "${APPLY_WORKFLOW}" '^  plan-dev:' "dev apply 전에 별도 dev plan을 다시 만들어야 한다."
assert_contains "${APPLY_WORKFLOW}" 'needs:.*apply-shared' "shared 적용 성공 뒤 dev plan을 생성해야 한다."
assert_contains "${APPLY_WORKFLOW}" 'uses:[[:space:]]*\./\.github/workflows/terraform-plan-environment\.yml' "merged plan은 trusted reusable boundary를 사용해야 한다."
assert_contains "${APPLY_WORKFLOW}" 'uses:[[:space:]]*\./\.github/workflows/terraform-apply-environment\.yml' "apply는 environment reusable boundary를 사용해야 한다."
assert_not_contains "${APPLY_WORKFLOW}" 'workflow_dispatch' "임의 ref에서 Terraform apply를 시작하면 안 된다."

assert_contains "${TRUSTED_PLAN_WORKFLOW}" 'environment:[[:space:]]*terraform-apply-plan' "merged plan writer는 review-plan과 다른 OIDC subject를 사용해야 한다."
assert_contains "${TRUSTED_PLAN_WORKFLOW}" 'MOIMYEON_TERRAFORM_APPLY_PLAN_ROLE_TO_ASSUME' "merged apply-plan은 전용 trusted writer role을 사용해야 한다."
assert_not_contains "${TRUSTED_PLAN_WORKFLOW}" 'MOIMYEON_TERRAFORM_REVIEW_PLAN_ROLE_TO_ASSUME' "review-plan role이 apply artifact prefix를 쓰면 안 된다."
assert_contains "${TRUSTED_PLAN_WORKFLOW}" '--if-none-match[[:space:]]+'\''\*'\''' "apply plan artifact key는 create-only여야 한다."
assert_contains "${TRUSTED_PLAN_WORKFLOW}" 'show -json' "apply 승인 전에 merged exact plan의 sanitized summary를 남겨야 한다."
assert_contains "${TRUSTED_PLAN_WORKFLOW}" 'Reject stale CI-successful revision before planning' "trusted plan도 latest CI-successful revision에서만 생성해야 한다."
assert_contains "${TRUSTED_PLAN_WORKFLOW}" 'resolve-deploy-candidate\.sh' "trusted plan은 trigger가 여전히 latest CI-successful인지 검증해야 한다."
assert_contains "${TRUSTED_PLAN_WORKFLOW}" 'Stale Terraform plan rejected' "stale Terraform boundary가 success로 보이면 app deploy가 먼저 시작할 수 있다."
assert_contains "${TRUSTED_PLAN_WORKFLOW}" 'queue:[[:space:]]*max' "latest plan pending run을 늦은 과거 run이 교체하면 안 된다."
assert_contains "${TRUSTED_PLAN_WORKFLOW}" 'inputs\.source_sha' "reusable plan은 caller의 CI-successful SHA를 사용해야 한다."

assert_contains "${ENV_APPLY_WORKFLOW}" 'environment:[[:space:]]*\$\{\{ inputs\.environment \}\}-infra' "환경별 infra 승인 경계가 필요하다."
assert_contains "${ENV_APPLY_WORKFLOW}" 'sha256sum --check' "apply 전 exact plan checksum을 확인해야 한다."
assert_contains "${ENV_APPLY_WORKFLOW}" 'terraform .*apply[[:space:]]+-input=false.*tfplan' "승인된 binary plan만 apply해야 한다."
assert_contains "${ENV_APPLY_WORKFLOW}" 'deploy-aws-\{0\}' "dev/live Terraform은 app deploy·rollback과 같은 mutation lock을 사용해야 한다."
assert_contains "${ENV_APPLY_WORKFLOW}" 'terraform-shared' "shared Terraform은 별도 account-level mutation lock을 사용해야 한다."
assert_contains "${ENV_APPLY_WORKFLOW}" 'queue:[[:space:]]*max' "pending exact-plan apply를 늦은 과거 run이 교체하면 안 된다."
assert_contains "${ENV_APPLY_WORKFLOW}" 'Reject stale CI-successful revision after mutation lock' "mutation lock 뒤 latest CI-successful SHA freshness를 다시 확인해야 한다."
assert_contains "${ENV_APPLY_WORKFLOW}" 'Stale Terraform apply rejected' "stale apply run은 boundary success로 끝나면 안 된다."
assert_contains "${ENV_APPLY_WORKFLOW}" 'REQUESTED_SHA.*inputs\.source_sha' "과거 push plan이 최신 infra를 되돌리면 안 된다."
assert_contains "${ENV_APPLY_WORKFLOW}" 'inputs\.source_sha' "reusable apply는 caller의 CI-successful SHA를 사용해야 한다."
assert_contains "${ENV_APPLY_WORKFLOW}" 'secrets\.MOIMYEON_TERRAFORM_VARIABLE_SYNC_TOKEN' "apply 후 Variables write 전용 credential로 output을 동기화해야 한다."
if sed -n '/name: Sync deployment variables/,+5p' "${ENV_APPLY_WORKFLOW}" \
  | grep -Eq 'GH_TOKEN:.*secrets\.GITHUB_TOKEN'; then
  fail "기본 GITHUB_TOKEN을 Variables write credential로 오인하면 안 된다."
fi

echo "Terraform CI 계약을 만족한다."
