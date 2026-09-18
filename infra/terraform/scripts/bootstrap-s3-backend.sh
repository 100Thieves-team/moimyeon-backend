#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  infra/terraform/scripts/bootstrap-s3-backend.sh [--region ap-northeast-2] [--bucket BUCKET] [--lock-table TABLE]

Creates the S3 bucket and DynamoDB lock table used by Terraform remote state,
then writes envs/shared/backend.tf, envs/dev/backend.tf, and envs/live/backend.tf.

Defaults:
  --region      ${AWS_REGION:-ap-northeast-2}
  --bucket      moimyeon-terraform-state-<aws-account-id>-<region>
  --lock-table  moimyeon-terraform-locks
EOF
}

REGION="${AWS_REGION:-ap-northeast-2}"
BUCKET=""
LOCK_TABLE="moimyeon-terraform-locks"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --region)
      REGION="$2"
      shift 2
      ;;
    --bucket)
      BUCKET="$2"
      shift 2
      ;;
    --lock-table)
      LOCK_TABLE="$2"
      shift 2
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

command -v aws >/dev/null 2>&1 || {
  echo "aws CLI is required." >&2
  exit 1
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TERRAFORM_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"

if [[ -z "${BUCKET}" ]]; then
  BUCKET="moimyeon-terraform-state-${ACCOUNT_ID}-${REGION}"
fi

echo "Terraform state bucket: ${BUCKET}"
echo "Terraform lock table:   ${LOCK_TABLE}"
echo "AWS region:             ${REGION}"

if aws s3api head-bucket --bucket "${BUCKET}" >/dev/null 2>&1; then
  echo "S3 bucket already exists: ${BUCKET}"
else
  echo "Creating S3 bucket: ${BUCKET}"
  if [[ "${REGION}" == "us-east-1" ]]; then
    aws s3api create-bucket \
      --bucket "${BUCKET}" \
      --region "${REGION}" >/dev/null
  else
    aws s3api create-bucket \
      --bucket "${BUCKET}" \
      --region "${REGION}" \
      --create-bucket-configuration "LocationConstraint=${REGION}" >/dev/null
  fi
fi

aws s3api put-public-access-block \
  --bucket "${BUCKET}" \
  --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true >/dev/null

aws s3api put-bucket-versioning \
  --bucket "${BUCKET}" \
  --versioning-configuration Status=Enabled >/dev/null

aws s3api put-bucket-encryption \
  --bucket "${BUCKET}" \
  --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}' >/dev/null

if aws dynamodb describe-table --table-name "${LOCK_TABLE}" --region "${REGION}" >/dev/null 2>&1; then
  echo "DynamoDB lock table already exists: ${LOCK_TABLE}"
else
  echo "Creating DynamoDB lock table: ${LOCK_TABLE}"
  aws dynamodb create-table \
    --table-name "${LOCK_TABLE}" \
    --attribute-definitions AttributeName=LockID,AttributeType=S \
    --key-schema AttributeName=LockID,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region "${REGION}" >/dev/null
  aws dynamodb wait table-exists --table-name "${LOCK_TABLE}" --region "${REGION}"
fi

write_backend() {
  local env_name="$1"
  local key="$2"
  local backend_file="${TERRAFORM_DIR}/envs/${env_name}/backend.tf"

  cat >"${backend_file}" <<EOF
terraform {
  backend "s3" {
    bucket         = "${BUCKET}"
    key            = "${key}"
    region         = "${REGION}"
    dynamodb_table = "${LOCK_TABLE}"
    encrypt        = true
  }
}
EOF

  echo "Wrote ${backend_file}"
}

write_backend "shared" "shared/terraform.tfstate"
write_backend "dev" "dev/terraform.tfstate"
write_backend "live" "live/terraform.tfstate"

cat <<EOF

Done.

Next:
  cd infra/terraform/envs/shared
  terraform init
  terraform apply

The reviewed non-secret environment sources are committed as shared.tfvars,
dev.tfvars, and live.tfvars. Do not create terraform.tfvars. Use
scripts/terraform-command.sh for official validate and plan commands.
EOF
