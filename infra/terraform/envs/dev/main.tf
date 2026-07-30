module "dev" {
  source = "../../modules/moimyeon-environment"

  project                  = var.project
  environment              = "dev"
  github_repository        = var.github_repository
  github_branch            = "dev"
  github_oidc_provider_arn = var.github_oidc_provider_arn

  route53_zone_id   = var.route53_zone_id
  route53_zone_name = var.route53_zone_name
  app_domain_name   = var.app_domain_name
  dns_management    = var.dns_management
  enable_https      = var.enable_https

  upload_cors_allowed_origins = var.upload_cors_allowed_origins

  vpc_cidr                  = var.vpc_cidr
  ecs_instance_type         = "t3.small"
  ecs_min_size              = 1
  ecs_desired_capacity      = 1
  ecs_max_size              = 2
  ecs_service_desired_count = 2
  ecs_service_min_count     = 2
  ecs_service_max_count     = 2

  rds_instance_class          = "db.t4g.micro"
  db_name                     = var.db_name
  db_username                 = var.db_username
  db_backup_retention_period  = 1
  db_deletion_protection      = false
  db_skip_final_snapshot      = true
  force_destroy_upload_bucket = true

  enable_db_bastion = true

  oauth_google_client_id     = var.oauth_google_client_id
  oauth_google_client_secret = var.oauth_google_client_secret

  tags = var.tags
}
