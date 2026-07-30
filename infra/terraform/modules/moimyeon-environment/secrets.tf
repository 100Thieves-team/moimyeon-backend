resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "aws_ssm_parameter" "jwt_secret" {
  name        = "/${var.project}/${var.environment}/core-api/JWT_SECRET"
  description = "JWT signing secret for ${local.name} core-api"
  type        = "SecureString"
  value       = random_password.jwt.result

  tags = local.tags
}

# moimyeon requires Google OAuth client secret to boot (security-core.yml, no default).
resource "aws_ssm_parameter" "oauth_google_client_secret" {
  name        = "/${var.project}/${var.environment}/core-api/GOOGLE_OAUTH_CLIENT_SECRET"
  description = "Google OAuth client secret for ${local.name} core-api"
  type        = "SecureString"
  value       = var.oauth_google_client_secret

  tags = local.tags
}

# Last deployed image URI. The deploy workflow overwrites this (ssm put-parameter),
# so Terraform ignores drift on the value.
resource "aws_ssm_parameter" "image_uri" {
  name        = "/${var.project}/${var.environment}/core-api/IMAGE_URI"
  description = "Last deployed container image URI for ${local.name} core-api"
  type        = "String"
  value       = local.image_uri

  lifecycle {
    ignore_changes = [value]
  }

  tags = local.tags
}

locals {
  # DB password ARN. generate mode: the TF-managed parameter. reference mode: a
  # constructed ARN pointing at the pre-created parameter, so the secret VALUE is
  # never read into Terraform state (unlike a data source, which would).
  db_password_param_name = "/${var.project}/${var.environment}/core-api/DB_PASSWORD"
  db_password_ssm_arn = (
    var.generate_db_password
    ? aws_ssm_parameter.db_password[0].arn
    : "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter${local.db_password_param_name}"
  )

  # Injected into the container as `secrets` (valueFrom SSM). The `name` is the
  # runtime env var the app reads; these match moimyeon's config contract.
  container_secrets = [
    {
      name      = "STORAGE_DATABASE_CORE_DB_PASSWORD"
      valueFrom = local.db_password_ssm_arn
    },
    {
      name      = "JWT_SECRET"
      valueFrom = aws_ssm_parameter.jwt_secret.arn
    },
    {
      name      = "GOOGLE_OAUTH_CLIENT_SECRET"
      valueFrom = aws_ssm_parameter.oauth_google_client_secret.arn
    },
  ]

  ssm_parameter_arns = [
    local.db_password_ssm_arn,
    aws_ssm_parameter.jwt_secret.arn,
    aws_ssm_parameter.oauth_google_client_secret.arn,
  ]
}
