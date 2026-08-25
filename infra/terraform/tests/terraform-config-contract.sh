#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TERRAFORM_DIR="${ROOT_DIR}/infra/terraform"

fail() {
  echo "Terraform 설정 계약 위반: $1" >&2
  exit 1
}

expected_tfvars="$(printf '%s\n' \
  infra/terraform/envs/dev/dev.tfvars \
  infra/terraform/envs/live/live.tfvars \
  infra/terraform/envs/shared/shared.tfvars)"
tracked_tfvars="$({
  git -C "${ROOT_DIR}" ls-files 'infra/terraform/**/*.tfvars'
  git -C "${ROOT_DIR}" ls-files 'infra/terraform/**/*.tfvars.json'
  git -C "${ROOT_DIR}" ls-files --others --exclude-standard 'infra/terraform/**/*.tfvars'
  git -C "${ROOT_DIR}" ls-files --others --exclude-standard 'infra/terraform/**/*.tfvars.json'
} | sort -u)"
if [ "${tracked_tfvars}" != "${expected_tfvars}" ]; then
  echo "Expected tracked tfvars:" >&2
  echo "${expected_tfvars}" >&2
  echo "Actual tracked tfvars:" >&2
  echo "${tracked_tfvars}" >&2
  fail "공식 비민감 tfvars 세 개만 Git에서 추적해야 한다."
fi

assert_keys() {
  local file="$1"
  shift
  local expected
  local actual
  expected="$(printf '%s\n' "$@" | sort)"
  actual="$(awk -F= '
    /^[[:space:]]*[A-Za-z_][A-Za-z0-9_]*[[:space:]]*=/ {
      key=$1
      gsub(/[[:space:]]/, "", key)
      print key
    }
  ' "${file}" | sort)"
  if [ "${actual}" != "${expected}" ]; then
    echo "Expected keys for ${file}:" >&2
    echo "${expected}" >&2
    echo "Actual keys:" >&2
    echo "${actual}" >&2
    fail "허용되지 않은 환경 설정 키가 있다."
  fi
}

common_keys=(
  app_domain_name
  aws_region
  db_master_username
  db_name
  db_username
  dns_management
  enable_https
  firebase_project_id
  github_repository
  notification_email_gmail_address
  notification_email_ses_from_address
  notification_web_push_action_base_url
  notification_worker_desired_count
  oauth_google_client_id
  upload_cors_allowed_origins
  vpc_cidr
)

assert_keys "${TERRAFORM_DIR}/envs/dev/dev.tfvars" "${common_keys[@]}"
assert_keys "${TERRAFORM_DIR}/envs/live/live.tfvars" "${common_keys[@]}"
assert_keys "${TERRAFORM_DIR}/envs/shared/shared.tfvars" \
  aws_region \
  create_hosted_zone \
  create_oidc_provider \
  github_immutable_repository \
  github_repository \
  github_repository_id \
  github_repository_owner_id \
  project \
  register_domain \
  route53_zone_name \
  terraform_lock_table_name \
  terraform_plan_artifact_bucket_name \
  terraform_state_bucket_name

if grep -RInE \
  '^[[:space:]]*(.*(secret|password|token|private_key).*)[[:space:]]*=[[:space:]]*"' \
  "${TERRAFORM_DIR}/envs"/*/*.tfvars; then
  fail "committed tfvars에 시크릿 성격의 key 또는 literal을 둘 수 없다."
fi

if grep -RInE 'REPLACE_WITH|CHANGE_ME|BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY' \
  "${TERRAFORM_DIR}" --include='*.tf' --include='*.tfvars'; then
  fail "공식 Terraform 설정에 placeholder 또는 private key literal을 둘 수 없다."
fi

if grep -RInE 'github_oidc_provider_arn[[:space:]]*=' \
  "${TERRAFORM_DIR}/envs"/*/*.tfvars; then
  fail "GitHub OIDC provider ARN은 AWS data source로 계산해야 한다."
fi

for environment in dev live shared; do
  backend_source="${TERRAFORM_DIR}/envs/${environment}/backend.tf.example"
  [ -f "${backend_source}" ] || fail "${environment} backend source가 없다."
  grep -Fq 'backend "s3"' "${backend_source}" \
    || fail "${environment} backend source는 credentials 없는 S3 backend여야 한다."
done

if grep -Fq 'resource "random_password" "jwt"' \
  "${TERRAFORM_DIR}/modules/moimyeon-environment/secrets.tf"; then
  fail "JWT 값은 Terraform state에서 생성하면 안 된다."
fi
if grep -Fq 'resource "aws_ssm_parameter" "jwt_secret"' \
  "${TERRAFORM_DIR}/modules/moimyeon-environment/secrets.tf"; then
  fail "JWT SecureString 값은 Terraform state가 소유하면 안 된다."
fi
grep -Fq 'manage_master_user_password' \
  "${TERRAFORM_DIR}/modules/moimyeon-environment/rds.tf" \
  || fail "신규 live RDS password는 Secrets Manager에 맡겨야 한다."
grep -Fq 'valueFrom = local.db_password_ssm_arn' \
  "${TERRAFORM_DIR}/modules/moimyeon-environment/secrets.tf" \
  || fail "ECS는 회전하는 RDS master secret이 아니라 별도 SSM application credential을 사용해야 한다."
if grep -Fq 'secretsmanager:GetSecretValue' \
  "${TERRAFORM_DIR}/modules/moimyeon-environment/iam.tf"; then
  fail "ECS execution role은 RDS master secret을 읽으면 안 된다."
fi

grep -Fq 'terraform-config-contract.sh' "${ROOT_DIR}/.github/workflows/ci.yml" \
  || fail "CI가 Terraform 설정 권위 계약을 검사해야 한다."

echo "Terraform 설정 권위 계약을 만족한다."
