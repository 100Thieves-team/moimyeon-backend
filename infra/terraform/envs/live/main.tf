data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

module "live" {
  source = "../../modules/moimyeon-environment"

  application_logging_mode = var.application_logging_mode

  project                  = var.project
  environment              = "live"
  github_repository        = var.github_repository
  github_branch            = "main"
  github_oidc_provider_arn = data.aws_iam_openid_connect_provider.github.arn
  # GitHub immutable OIDC subject prefix (numeric org/repo IDs).
  # From: gh api /repos/100Thieves-team/moimyeon-backend/actions/oidc/customization/sub
  github_deploy_immutable_repo = "100Thieves-team@278404932/moimyeon-backend@1307286446"
  github_deploy_environments   = ["live-app"]
  github_repository_id         = "1307286446"
  github_repository_owner_id   = "278404932"
  github_deploy_workflows      = ["Promote Live", "Rollback AWS"]
  github_deploy_execution_refs = ["refs/heads/dev"]
  github_deploy_additional_ecr_read_repository_arns = [
    "arn:aws:ecr:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:repository/${var.promotion_source_api_ecr_repository_name}",
    "arn:aws:ecr:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:repository/${var.promotion_source_worker_ecr_repository_name}",
  ]
  # MOI-512: promotion reads task templates from SSM after the Terraform boundary.
  publish_deploy_config = true
  github_deploy_additional_ssm_read_parameter_arns = [
    "arn:aws:ssm:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:parameter/${var.project}/dev/deployments/*",
  ]

  route53_zone_id   = var.route53_zone_id
  route53_zone_name = var.route53_zone_name
  app_domain_name   = var.app_domain_name
  dns_management    = var.dns_management
  enable_https      = var.enable_https

  upload_cors_allowed_origins = var.upload_cors_allowed_origins

  vpc_cidr = var.vpc_cidr

  # MOI-581: steady state needs two t3.small instances (API alone on one,
  # Worker and Redis on the other). Live deploys only on main merges, so no
  # spare instance is kept: a blue/green deploy lets ECS managed scaling add one
  # (about four minutes) and scales back in afterwards. Max 4 also covers a
  # second API task.
  ecs_instance_type         = "t3.small"
  ecs_min_size              = 2
  ecs_max_size              = 4
  ecs_service_desired_count = 1
  ecs_service_min_count     = 1
  ecs_service_max_count     = 2
  ecs_deployment_strategy   = "BLUE_GREEN"

  # MOI-581: no deployment alarms are attached, so the bake window only waited.
  # Keep a one-minute window and match dev's health check and drain timings;
  # a promotion then takes about 5-7 minutes instead of 12-14.
  ecs_blue_green_bake_time_in_minutes = 1
  alb_health_check_interval_seconds   = 10
  alb_deregistration_delay_seconds    = 10

  # Same as dev: the API task takes a whole t3.small so a cold JVM connects to
  # MySQL within the connection timeout.
  task_cpu    = 2048
  task_memory = 1600

  rds_instance_class         = "db.t4g.micro"
  db_name                    = var.db_name
  db_username                = var.db_username
  db_master_username         = var.db_master_username
  generate_db_password       = false
  manage_db_master_password  = true
  db_backup_retention_period = 7
  db_deletion_protection     = true
  db_skip_final_snapshot     = false

  enable_db_bastion = true

  # A single Redis task with AOF on EFS, as in dev. High availability
  # (replication/Sentinel) is a later follow-up (MOI-581).
  enable_notification_redis = true

  notification_worker_desired_count     = var.notification_worker_desired_count
  firebase_project_id                   = var.firebase_project_id
  notification_web_push_action_base_url = var.notification_web_push_action_base_url
  notification_email_ses_from_address   = var.notification_email_ses_from_address
  notification_email_gmail_address      = var.notification_email_gmail_address

  oauth_google_client_id = var.oauth_google_client_id

  tags = var.tags
}
