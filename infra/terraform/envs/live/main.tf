module "live" {
  source = "../../modules/moimyeon-environment"

  project                  = var.project
  environment              = "live"
  github_repository        = var.github_repository
  github_branch            = "main"
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

  vpc_cidr          = var.vpc_cidr
  ecs_instance_type = "t3.small"

  # Provisioned but scaled to zero until live is intentionally brought up.
  # Raise these (e.g. asg 2/2/3, service 2/2/4) when going live.
  ecs_min_size              = 0
  ecs_desired_capacity      = 0
  ecs_max_size              = 0
  ecs_service_desired_count = 0
  ecs_service_min_count     = 0
  ecs_service_max_count     = 0

  rds_instance_class         = "db.t4g.micro"
  db_name                    = var.db_name
  db_username                = var.db_username
  db_backup_retention_period = 7
  db_deletion_protection     = true
  db_skip_final_snapshot     = false

  enable_db_bastion = true

  oauth_google_client_id     = var.oauth_google_client_id
  oauth_google_client_secret = var.oauth_google_client_secret

  tags = var.tags
}
