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
  # Injected into the container as `secrets` (valueFrom SSM). The `name` is the
  # runtime env var the app reads; these match moimyeon's config contract.
  container_secrets = [
    {
      name      = "STORAGE_DATABASE_CORE_DB_PASSWORD"
      valueFrom = aws_ssm_parameter.db_password.arn
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
    aws_ssm_parameter.db_password.arn,
    aws_ssm_parameter.jwt_secret.arn,
    aws_ssm_parameter.oauth_google_client_secret.arn,
  ]
}
