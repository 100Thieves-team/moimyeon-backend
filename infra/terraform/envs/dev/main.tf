module "dev" {
  source = "../../modules/moimyeon-environment"

  project                  = var.project
  environment              = "dev"
  github_repository        = var.github_repository
  github_branch            = "dev"
  github_oidc_provider_arn = var.github_oidc_provider_arn
  # GitHub immutable OIDC subject prefix (numeric org/repo IDs).
  # From: gh api /repos/100Thieves-team/moimyeon-backend/actions/oidc/customization/sub
  github_deploy_immutable_repo = "100Thieves-team@278404932/moimyeon-backend@1307286446"

  route53_zone_id   = var.route53_zone_id
  route53_zone_name = var.route53_zone_name
  app_domain_name   = var.app_domain_name
  dns_management    = var.dns_management
  enable_https      = var.enable_https

  upload_cors_allowed_origins = var.upload_cors_allowed_origins

  vpc_cidr           = var.vpc_cidr
  availability_zones = ["ap-northeast-2a", "ap-northeast-2c"]

  # Blue/green cutover: create a distinct ip target group and DO NOT manage the
  # existing ALB listeners yet, so the current EC2 container keeps serving until
  # we switch the listener over (post-apply, controlled step).
  target_group_name    = "moimyeon-dev-tg-ecs"
  manage_alb_listeners = false
  # Attach the new TG to an internal-only :8080 listener so ECS accepts it and the
  # ALB health-checks the green tasks. Live :80/:443 still route to the old TG.
  provisional_ecs_listener_port = 8080

  ecs_instance_type    = "t3.small"
  ecs_min_size         = 1
  ecs_desired_capacity = 1
  ecs_max_size         = 2
  # Start with a single task for a safe first bring-up on one t3.small; scale later.
  ecs_service_desired_count = 1
  ecs_service_min_count     = 1
  ecs_service_max_count     = 2

  # The app's dev profile uses an aggressive HikariCP connection-timeout (1100ms).
  # On 0.5 vCPU (task_cpu=512) the cold JVM + MySQL 8.4 caching_sha2 handshake
  # can't connect within 1.1s. Give the single task the full t3.small (2 vCPU).
  task_cpu    = 2048
  task_memory = 1600

  rds_instance_class   = "db.t4g.micro"
  mysql_engine_version = "8.4.9"
  ecr_repository_name  = "moimyeon/backend"
  db_name              = var.db_name
  db_username          = var.db_username
  # Absorb the existing dev DB without rotating its master password. The current
  # app.env password must be put into SSM /moimyeon/dev/core-api/DB_PASSWORD first.
  generate_db_password        = false
  db_backup_retention_period  = 1
  db_deletion_protection      = false
  db_skip_final_snapshot      = true
  force_destroy_upload_bucket = true

  # The existing hand-built bastion (i-0f5cbc…, sg-0060…, role
  # moimyeon-dev-role-ssm-db-access) already works and is left UNMANAGED to avoid
  # name/CIDR collisions. Its RDS access is preserved via extra_rds_ingress below.
  # (Import or rebuild it under Terraform as a later follow-up.)
  enable_db_bastion = false

  # Dev keeps a single node to limit cost. Live requires at least two nodes and
  # automatic failover when the notification Redis switch is enabled there.
  enable_notification_redis                  = true
  notification_redis_node_type               = "cache.t4g.micro"
  notification_redis_node_count              = 1
  notification_redis_snapshot_retention_days = 1

  # Transitional (blue/green): keep the existing app-host SG (moimyeon-dev-sg-app)
  # and the existing bastion SG (moimyeon-dev-sg-db-access) able to reach RDS so
  # the current container/tunnel keep working during the absorb. Drop after cutover.
  extra_rds_ingress_security_group_ids = [
    "sg-00d77f45be3f6fb78", # moimyeon-dev-sg-app (current EC2 docker host)
    "sg-0060dc7b466210641", # moimyeon-dev-sg-db-access (existing bastion)
  ]

  # Match the immutable descriptions of the imported hand-built SGs (imports.tf).
  alb_sg_description = "Allow public HTTP/HTTPS traffic to the Moimyeon development ALB"
  rds_sg_description = "Security group for moimyeon dev MySQL RDS"

  oauth_google_client_id     = var.oauth_google_client_id
  oauth_google_client_secret = var.oauth_google_client_secret

  tags = var.tags
}
