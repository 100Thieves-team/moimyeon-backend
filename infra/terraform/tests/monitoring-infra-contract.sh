#!/usr/bin/env bash
set -euo pipefail

TERRAFORM_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MONITORING_MODULE="${TERRAFORM_ROOT}/modules/moimyeon-environment"
MONITORING_TF="${MONITORING_MODULE}/monitoring.tf"
MONITORING_IAM="${MONITORING_MODULE}/monitoring_iam.tf"
MONITORING_S3="${MONITORING_MODULE}/monitoring_s3.tf"
SHARED_CI="${TERRAFORM_ROOT}/modules/shared-foundation/terraform_ci.tf"

require_literal() {
  if ! grep -Fq -- "$1" "$2"; then
    echo "Missing monitoring contract: $1 ($2)" >&2
    exit 1
  fi
}

reject_pattern() {
  if grep -Eq -- "$1" "$2"; then
    echo "Unsafe monitoring contract: $1 ($2)" >&2
    exit 1
  fi
}

require_resource_literal() {
  local resource_type="$1" resource_name="$2" literal="$3"
  if ! sed -n "/^resource \"${resource_type}\" \"${resource_name}\" {/,/^}/p" "${MONITORING_TF}" |
    grep -Fq -- "${literal}"; then
    echo "Missing monitoring resource contract: ${resource_type}.${resource_name}: ${literal}" >&2
    exit 1
  fi
}

# No accidental live activation or public administrative network surface.
sed -n '/variable "enable_monitoring" {/,/^}/p' "${MONITORING_MODULE}/monitoring_variables.tf" |
  grep -Eq 'default[[:space:]]*=[[:space:]]*false' || exit 1
reject_pattern 'enable_monitoring[[:space:]]*=[[:space:]]*true' "${TERRAFORM_ROOT}/envs/live/main.tf"
reject_pattern 'enable_monitoring[[:space:]]*=[[:space:]]*true' "${TERRAFORM_ROOT}/envs/live/live.tfvars"
grep -Eq '^monitoring_ami_id[[:space:]]*=[[:space:]]*"ami-[0-9a-f]+"' "${TERRAFORM_ROOT}/envs/dev/dev.tfvars" || exit 1
require_literal 'var.environment == "dev" && var.enable_notification_redis && var.enable_nat_gateway' "${MONITORING_TF}"
require_literal 'associate_public_ip_address = false' "${MONITORING_TF}"
reject_pattern 'from_port[[:space:]]*=[[:space:]]*(22|3000|9090|9464|4317)([[:space:]]|$)' "${MONITORING_TF}"
require_literal 'referenced_security_group_id = aws_security_group.ecs_task.id' "${MONITORING_TF}"
require_literal 'referenced_security_group_id = aws_security_group.notification_worker_task.id' "${MONITORING_TF}"
if sed -n '/resource "aws_vpc_security_group_ingress_rule"/,/^}/p' "${MONITORING_TF}" | grep -Eq 'cidr_ipv[46][[:space:]]*='; then
  echo 'Monitoring ingress must reference API/Worker security groups, never CIDRs.' >&2
  exit 1
fi
require_literal 'resource "aws_vpc_security_group_egress_rule" "monitoring_worker_otlp"' "${MONITORING_TF}"
require_literal 'monitoring_otlp_metrics_url       = "http://${local.monitoring_hostname}:4318/v1/metrics"' "${MONITORING_TF}"

# Cloud Map owns the existing namespace's hosted zone. Direct Route53 writes
# fail at apply even when validate/plan succeed. Keep the existing hostname and
# register the host through Cloud Map without taking over Redis or its namespace.
reject_pattern '^resource[[:space:]]+"aws_route53_record"' "${MONITORING_TF}"
reject_pattern 'aws_service_discovery_private_dns_namespace\.notification\[0\]\.hosted_zone' "${MONITORING_TF}"
require_literal '"monitoring.${local.name}.internal"' "${MONITORING_TF}"
for resource_type in aws_service_discovery_service aws_service_discovery_instance; do
  require_resource_literal "${resource_type}" monitoring 'count = var.enable_monitoring ? 1 : 0'
done
require_resource_literal aws_service_discovery_service monitoring 'name = "monitoring"'
require_resource_literal aws_service_discovery_service monitoring 'namespace_id   = aws_service_discovery_private_dns_namespace.notification[0].id'
require_resource_literal aws_service_discovery_service monitoring 'routing_policy = "MULTIVALUE"'
require_resource_literal aws_service_discovery_service monitoring 'ttl  = 30'
require_resource_literal aws_service_discovery_service monitoring 'type = "A"'
require_resource_literal aws_service_discovery_instance monitoring 'instance_id = "monitoring"'
require_resource_literal aws_service_discovery_instance monitoring 'service_id  = aws_service_discovery_service.monitoring[0].id'
require_resource_literal aws_service_discovery_instance monitoring 'AWS_INSTANCE_IPV4 = aws_instance.monitoring[0].private_ip'
# No component updates Cloud Map health; DNS registration must not imply a
# Collector readiness check or copy Redis's ECS-managed custom health contract.
reject_pattern '^[[:space:]]*health_check(_custom)?_config[[:space:]]*\{' "${MONITORING_TF}"

# Data survives normal host replacement. No forced or live volume detach.
require_literal 'resource "aws_ebs_volume" "monitoring_data"' "${MONITORING_TF}"
require_literal 'prevent_destroy = true' "${MONITORING_TF}"
require_literal 'stop_instance_before_detaching = true' "${MONITORING_TF}"
require_literal 'force_detach                   = false' "${MONITORING_TF}"
sed -n '/resource "aws_ebs_volume" "monitoring_data" {/,/^}/p' "${MONITORING_TF}" |
  grep -Eq 'encrypted[[:space:]]*=[[:space:]]*true' || exit 1
require_literal 'http_tokens                 = "required"' "${MONITORING_TF}"
require_literal 'http_put_response_hop_limit = 1' "${MONITORING_TF}"

# Only the host reads the Grafana password at runtime. The managed SSM policy's
# broad parameter grant is bounded explicitly, not merely shadowed by an Allow.
require_literal 'sid           = "DenyUnrelatedParameterValues"' "${MONITORING_IAM}"
require_literal 'not_resources = [local.monitoring_grafana_parameter_arn]' "${MONITORING_IAM}"
reject_pattern '"(kms:\*|kms:Decrypt|ssm:\*|s3:\*)"' "${MONITORING_IAM}"
reject_pattern '^resource "aws_ssm_parameter"' "${MONITORING_TF}"
sed -n '/data "aws_ssm_parameter" "monitoring_ami" {/,/^}/p' "${MONITORING_TF}" |
  grep -Fq 'with_decryption = false' || exit 1
require_literal 'grafana_parameter    = local.monitoring_grafana_parameter_arn' "${MONITORING_TF}"
require_literal 'base64gzip(templatefile(' "${MONITORING_TF}"

# Immutable, checksummed config release; neither S3 nor Terraform stores logs or
# Grafana credentials. Existing awslogs paths remain intact on both app tasks.
for asset in compose.yaml otel-collector.yaml prometheus.yaml start-monitoring.sh \
  grafana/provisioning/datasources/prometheus.yaml \
  grafana/provisioning/dashboards/default.yaml grafana/dashboards/dev-overview.json; do
  require_literal "\"${asset}\"" "${MONITORING_TF}"
  test -f "${TERRAFORM_ROOT}/../observability/${asset}"
done
require_literal 'sha256 = filesha256(' "${MONITORING_TF}"
require_literal 'monitoring_config_prefix = "releases/${sha256(jsonencode(local.monitoring_config_manifest))}"' "${MONITORING_TF}"
require_literal 'block_public_policy     = true' "${MONITORING_S3}"
require_literal 'sse_algorithm = "AES256"' "${MONITORING_S3}"
require_literal 'variable = "aws:SecureTransport"' "${MONITORING_S3}"
require_literal 'logDriver = "awslogs"' "${MONITORING_MODULE}/application_logging.tf"
require_literal 'local.logging_task_policy["api"].app_settings' "${MONITORING_MODULE}/ecs.tf"
require_literal 'local.logging_task_policy["worker"].app_settings' "${MONITORING_MODULE}/worker_ecs.tf"
# Native plan tests cover the rendered task definitions, not merely this wiring.
require_literal 'run "disabled_preserves_legacy_tasks"' "${MONITORING_MODULE}/tests/logging.tftest.hcl"
require_literal 'terraform -chdir=infra/terraform/modules/moimyeon-environment test -no-color' "${TERRAFORM_ROOT}/../../.github/workflows/terraform-plan.yml"
require_literal 'local.monitoring_environment,' "${MONITORING_MODULE}/ecs.tf"
require_literal 'local.monitoring_environment,' "${MONITORING_MODULE}/worker_ecs.tf"
require_literal 'var.enable_monitoring ? [local.sentry_api_parameter_arn] : []' "${MONITORING_MODULE}/secrets.tf"
require_literal 'var.enable_monitoring ? [local.sentry_worker_parameter_arn] : []' "${MONITORING_MODULE}/secrets.tf"

# CI refresh receives only public AMI + non-secret dev config reads. The deny
# list covers the newly referenced SecureStrings as well as existing secrets.
require_literal '"s3:GetObject", "s3:GetObjectTagging"' "${SHARED_CI}"
require_literal 'arn:aws:s3:::${var.project}-dev-monitoring-config-${data.aws_caller_identity.current.account_id}/releases/*' "${SHARED_CI}"
require_literal 'arn:aws:ssm:${data.aws_region.current.region}::parameter/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64' "${SHARED_CI}"
for secret_path in core-api/SENTRY_DSN core-worker/SENTRY_DSN monitoring/GRAFANA_ADMIN_PASSWORD; do
  sed -n '/sid    = "DenyApplicationSecureStringReads"/,/^  }/p' "${SHARED_CI}" |
    grep -Fq "${secret_path}" || exit 1
done
require_literal 'sid    = "DenyApplicationSecretValueReads"' "${SHARED_CI}"
require_literal 'sid       = "DenyGeneralKMSDecryption"' "${SHARED_CI}"

echo "Monitoring Terraform contracts passed."
