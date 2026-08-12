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

resource "aws_ssm_parameter" "notification_worker_image_uri" {
  name        = "/${var.project}/${var.environment}/core-worker/IMAGE_URI"
  description = "Last deployed container image URI for ${local.name} core-worker"
  type        = "String"
  value       = "${aws_ecr_repository.notification_worker.repository_url}:${coalesce(var.notification_worker_image_tag, var.environment)}"

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

  firebase_service_account_param_name    = "/${var.project}/${var.environment}/core-worker/FIREBASE_SERVICE_ACCOUNT_JSON"
  gmail_app_password_param_name          = "/${var.project}/${var.environment}/core-worker/NOTIFICATION_EMAIL_GMAIL_APP_PASSWORD"
  firebase_service_account_ssm_arn       = "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter${local.firebase_service_account_param_name}"
  gmail_app_password_ssm_arn             = "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter${local.gmail_app_password_param_name}"
  notification_redis_password_param_name = "/${var.project}/${var.environment}/notification-redis/PASSWORD"
  notification_redis_url_param_name      = "/${var.project}/${var.environment}/shared/STORAGE_REDIS_URL"
  notification_redis_password_ssm_arn    = "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter${local.notification_redis_password_param_name}"
  notification_redis_url_ssm_arn         = "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter${local.notification_redis_url_param_name}"

  # Injected into the container as `secrets` (valueFrom SSM). The `name` is the
  # runtime env var the app reads; these match moimyeon's config contract.
  container_secrets = concat(
    [
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
    ],
    var.enable_notification_redis ? [
      {
        name      = "STORAGE_REDIS_URL"
        valueFrom = local.notification_redis_url_ssm_arn
      },
    ] : [],
  )

  ssm_parameter_arns = concat(
    [
      local.db_password_ssm_arn,
      aws_ssm_parameter.jwt_secret.arn,
      aws_ssm_parameter.oauth_google_client_secret.arn,
    ],
    var.enable_notification_redis ? [local.notification_redis_url_ssm_arn] : [],
  )


  notification_worker_secrets = concat(
    [
      {
        name      = "STORAGE_DATABASE_CORE_DB_PASSWORD"
        valueFrom = local.db_password_ssm_arn
      },
      {
        name      = "FIREBASE_SERVICE_ACCOUNT_JSON"
        valueFrom = local.firebase_service_account_ssm_arn
      },
      {
        name      = "NOTIFICATION_EMAIL_GMAIL_APP_PASSWORD"
        valueFrom = local.gmail_app_password_ssm_arn
      },
    ],
    var.enable_notification_redis ? [
      {
        name      = "STORAGE_REDIS_URL"
        valueFrom = local.notification_redis_url_ssm_arn
      },
    ] : [],
  )

  notification_worker_ssm_parameter_arns = concat(
    [
      local.db_password_ssm_arn,
      local.firebase_service_account_ssm_arn,
      local.gmail_app_password_ssm_arn,
    ],
    var.enable_notification_redis ? [local.notification_redis_url_ssm_arn] : [],
  )
}
