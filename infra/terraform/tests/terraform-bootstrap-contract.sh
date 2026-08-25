#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
BOOTSTRAP_TF="${ROOT_DIR}/infra/terraform/modules/shared-foundation/terraform_ci.tf"
SYNC_SCRIPT="${ROOT_DIR}/infra/terraform/scripts/sync-terraform-bootstrap.sh"
SHARED_OUTPUTS="${ROOT_DIR}/infra/terraform/envs/shared/outputs.tf"

fail() {
  echo "Terraform bootstrap 계약 위반: $1" >&2
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

[ -f "${BOOTSTRAP_TF}" ] || fail "shared foundation에 Terraform CI bootstrap 리소스가 없다."
[ -x "${SYNC_SCRIPT}" ] || fail "GitHub bootstrap 동기화 스크립트가 실행 가능하지 않다."

assert_contains "${BOOTSTRAP_TF}" 'resource "aws_kms_key" "terraform_plan_artifacts"' "exact plan 전용 KMS key가 필요하다."
assert_contains "${BOOTSTRAP_TF}" 'enable_key_rotation[[:space:]]*=[[:space:]]*true' "plan KMS key rotation을 켜야 한다."
assert_contains "${BOOTSTRAP_TF}" 'prevent_destroy[[:space:]]*=[[:space:]]*true' "plan bucket과 KMS key를 실수로 파괴하면 안 된다."
assert_contains "${BOOTSTRAP_TF}" 'resource "aws_s3_bucket_public_access_block" "terraform_plan_artifacts"' "plan bucket public access를 차단해야 한다."
assert_contains "${BOOTSTRAP_TF}" 'resource "aws_s3_bucket_versioning" "terraform_plan_artifacts"' "plan bucket versioning이 필요하다."
assert_contains "${BOOTSTRAP_TF}" 'expire-exact-plans-after-24-hours' "raw plan은 24시간 뒤 만료해야 한다."
assert_contains "${BOOTSTRAP_TF}" 'DenyArtifactDeletion' "CI가 raw plan을 삭제하면 안 된다."
assert_contains "${BOOTSTRAP_TF}" 'DenyUploadsWithoutKMS' "KMS를 우회한 upload를 거부해야 한다."
assert_contains "${BOOTSTRAP_TF}" 'DenyUploadsWithWrongKMSKey' "다른 KMS key를 사용한 upload를 거부해야 한다."
assert_contains "${BOOTSTRAP_TF}" 'DenyNonConditionalArtifactWrites' "조건 없는 overwrite 가능 upload를 거부해야 한다."
assert_contains "${BOOTSTRAP_TF}" 's3:if-none-match' "bucket policy가 create-only conditional write를 강제해야 한다."
assert_contains "${BOOTSTRAP_TF}" 'CrossPrefixWrites' "writer가 다른 artifact prefix에 쓰면 bucket policy가 거부해야 한다."

assert_contains "${BOOTSTRAP_TF}" 'terraform-review-plan' "PR plan writer role이 필요하다."
assert_contains "${BOOTSTRAP_TF}" 'artifact_prefix[[:space:]]*=[[:space:]]*"plans/pr"' "PR plan writer는 plans/pr prefix만 소유해야 한다."
assert_contains "${BOOTSTRAP_TF}" 'terraform-drift-plan' "drift plan writer role이 필요하다."
assert_contains "${BOOTSTRAP_TF}" 'artifact_prefix[[:space:]]*=[[:space:]]*"plans/drift"' "drift writer는 plans/drift prefix만 소유해야 한다."
assert_contains "${BOOTSTRAP_TF}" 'terraform-apply-plan' "merged plan writer role이 필요하다."
assert_contains "${BOOTSTRAP_TF}" 'artifact_prefix[[:space:]]*=[[:space:]]*"plans/apply"' "merged plan writer는 plans/apply prefix만 소유해야 한다."
assert_contains "${BOOTSTRAP_TF}" 'environment:\$\{each\.key\}' "OIDC trust는 GitHub Environment subject를 사용해야 한다."
assert_contains "${BOOTSTRAP_TF}" 'token.actions.githubusercontent.com:repository_id' "보호 없는 Environment는 immutable repository ID와 함께 검증해야 한다."
assert_contains "${BOOTSTRAP_TF}" 'token.actions.githubusercontent.com:repository_owner_id' "GitHub organization ID도 OIDC trust에서 검증해야 한다."
assert_contains "${BOOTSTRAP_TF}" 'token.actions.githubusercontent.com:workflow' "OIDC trust는 허용 workflow 이름을 검증해야 한다."
assert_contains "${BOOTSTRAP_TF}" 'token.actions.githubusercontent.com:ref' "privileged OIDC trust는 default branch 실행 ref를 검증해야 한다."
assert_contains "${BOOTSTRAP_TF}" 'token.actions.githubusercontent.com:job_workflow_ref' "apply OIDC trust는 고정 reusable workflow를 검증해야 한다."
assert_contains "${BOOTSTRAP_TF}" 'terraform-sync-variables\.yml@refs/heads/dev' "variable sync role도 고정 reusable workflow에서만 assume해야 한다."
assert_contains "${BOOTSTRAP_TF}" 'DenyReadingOrDeletingExactPlanArtifacts' "writer는 raw plan을 다시 읽을 수 없어야 한다."
assert_contains "${BOOTSTRAP_TF}" 'DenyApplicationSecretValueReads' "plan role은 Secrets Manager와 SSM path secret을 읽으면 안 된다."
assert_contains "${BOOTSTRAP_TF}" 'DenyApplicationSecureStringReads' "plan role은 application SecureString path를 읽으면 안 된다."
assert_contains "${BOOTSTRAP_TF}" 'DenyGeneralKMSDecryption' "plan role의 managed ReadOnly policy가 KMS decrypt를 열면 안 된다."
assert_not_contains "${BOOTSTRAP_TF}" 'aws:policy/ReadOnlyAccess' "AWS managed ReadOnlyAccess가 새 data-plane read를 열면 안 된다."
assert_contains "${BOOTSTRAP_TF}" 'RefreshTerraformResourceMetadata' "plan refresh는 저장소 소유 metadata allowlist를 사용해야 한다."
assert_contains "${BOOTSTRAP_TF}" 'application-autoscaling:ListTagsForResource' "Application Auto Scaling target refresh에는 tag 조회 권한이 필요하다."
assert_contains "${BOOTSTRAP_TF}" 'RefreshTerraformBucketMetadata' "plan role은 S3 object 대신 bucket metadata만 읽어야 한다."
assert_contains "${BOOTSTRAP_TF}" 'ReadNonSecretTerraformParameters' "plan role은 public AMI와 image marker만 읽어야 한다."
assert_contains "${BOOTSTRAP_TF}" 'DenyExactPlanArtifactMutation' "apply role은 approved plan을 바꿀 수 없어야 한다."
assert_contains "${BOOTSTRAP_TF}" 'DenyCrossEnvironmentStateObjects' "apply role은 다른 환경 state를 읽거나 되돌릴 수 없어야 한다."
assert_contains "${BOOTSTRAP_TF}" 'dynamodb:LeadingKeys' "state lock CRUD도 허용된 environment key로 제한해야 한다."
assert_contains "${BOOTSTRAP_TF}" 's3:GetObjectVersion' "다른 환경의 versioned state 과거본도 읽을 수 없어야 한다."
assert_contains "${BOOTSTRAP_TF}" 's3:DeleteObjectVersion' "다른 환경의 versioned state 과거본을 영구 삭제할 수 없어야 한다."
assert_contains "${BOOTSTRAP_TF}" 's3:DeleteObjectTagging' "다른 환경 state metadata mutation도 막아야 한다."

assert_contains "${SHARED_OUTPUTS}" 'terraform_plan_artifact_bucket_name' "plan bucket output이 필요하다."
assert_contains "${SHARED_OUTPUTS}" 'terraform_apply_role_arns' "환경별 apply role output이 필요하다."

assert_contains "${SYNC_SCRIPT}" 'The default is a read-only dry run' "GitHub bootstrap은 기본 read-only여야 한다."
assert_contains "${SYNC_SCRIPT}" 'phase environments' "Terraform role variable namespace를 만드는 phase가 필요하다."
assert_contains "${SYNC_SCRIPT}" 'phase variables' "AWS apply 뒤 output Variables를 별도 단계로 동기화해야 한다."
assert_contains "${SYNC_SCRIPT}" 'reviewers: \[\]' "Terraform Environment에는 required reviewer를 두지 않아야 한다."
assert_contains "${SYNC_SCRIPT}" 'prevent_self_review: false' "보호 없는 namespace에 self-review 설정을 남기면 안 된다."
assert_contains "${SYNC_SCRIPT}" 'deployment_branch_policy: null' "Terraform Environment에 deployment branch policy를 두지 않아야 한다."
assert_not_contains "${SYNC_SCRIPT}" '--reviewer|prevent_self_review: true|custom_branch_policies: true' "Terraform Environment 보호 규칙을 다시 추가하면 안 된다."
assert_contains "${SYNC_SCRIPT}" 'TERRAFORM_COMMAND=.*terraform-command\.sh' "output sync는 공식 Terraform wrapper를 선택해야 한다."
assert_contains "${SYNC_SCRIPT}" 'output-raw shared' "raw output도 공식 backend/workspace 경계를 거쳐야 한다."
assert_contains "${SYNC_SCRIPT}" 'output-json shared' "JSON output도 공식 backend/workspace 경계를 거쳐야 한다."
assert_contains "${SYNC_SCRIPT}" 'state-list dev' "plan role Variables 노출 전 current dev state를 검사해야 한다."
assert_contains "${SYNC_SCRIPT}" 'module\.dev\.aws_ssm_parameter\.jwt_secret' "JWT state forget 완료를 확인해야 한다."
assert_contains "${SYNC_SCRIPT}" 'module\.dev\.aws_ssm_parameter\.oauth_google_client_secret' "OAuth state forget 완료를 확인해야 한다."
assert_contains "${SYNC_SCRIPT}" 'module\.dev\.random_password\.jwt' "JWT random state forget 완료를 확인해야 한다."
assert_contains "${SYNC_SCRIPT}" 'Refusing to expose Terraform plan role Variables' "secret-bearing state에서는 role ARN sync를 fail-closed해야 한다."
assert_contains "${SYNC_SCRIPT}" 'configure_environment terraform-review-plan' "PR plan role variable namespace가 필요하다."
assert_contains "${SYNC_SCRIPT}" 'configure_environment shared-infra' "shared apply role variable namespace가 필요하다."
assert_contains "${SYNC_SCRIPT}" 'configure_environment dev-infra' "dev apply role variable namespace가 필요하다."
assert_contains "${SYNC_SCRIPT}" 'configure_environment live-infra' "live apply role variable namespace가 필요하다."
assert_contains "${SYNC_SCRIPT}" 'MOIMYEON_TERRAFORM_CI_ENABLED false' "bootstrap 스크립트는 CI를 비활성 상태로 남겨야 한다."
assert_not_contains "${SYNC_SCRIPT}" 'MOIMYEON_TERRAFORM_CI_ENABLED true' "preflight 전에 Terraform CI를 켜면 안 된다."
assert_contains "${SYNC_SCRIPT}" 'MOIMYEON_TERRAFORM_LIVE_CI_ENABLED false' "bootstrap은 live Terraform apply를 별도 false로 고정해야 한다."
assert_not_contains "${SYNC_SCRIPT}" 'MOIMYEON_TERRAFORM_LIVE_CI_ENABLED true' "bootstrap이 live Terraform을 활성화하면 안 된다."
assert_contains "${SYNC_SCRIPT}" 'MOIMYEON_LIVE_DEPLOY_ENABLED false' "bootstrap은 live application promotion도 false로 고정해야 한다."
assert_not_contains "${SYNC_SCRIPT}" 'MOIMYEON_LIVE_DEPLOY_ENABLED true' "bootstrap이 live application promotion을 활성화하면 안 된다."
assert_contains "${SYNC_SCRIPT}" 'MOIMYEON_LIVE_ROLLBACK_ENABLED false' "bootstrap은 live rollback mutation도 false로 고정해야 한다."
assert_not_contains "${SYNC_SCRIPT}" 'MOIMYEON_LIVE_ROLLBACK_ENABLED true' "bootstrap이 live rollback을 활성화하면 안 된다."
assert_not_contains "${SYNC_SCRIPT}" 'gh secret set.*MOIMYEON_TERRAFORM_VARIABLE_SYNC_TOKEN' "Variables-write credential 값은 자동화가 취급하면 안 된다."
assert_contains "${SYNC_SCRIPT}" 'MOIMYEON_TERRAFORM_VARIABLE_SYNC_TOKEN_PARAMETER' "GitHub에는 Variables-write credential의 SSM 이름만 동기화해야 한다."

echo "Terraform bootstrap 계약을 만족한다."
