data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name                               = "${var.project}-${var.environment}"
  profile                            = var.environment == "live" ? "live" : "dev"
  image_tag                          = coalesce(var.container_image_tag, var.environment)
  image_uri                          = "${aws_ecr_repository.app.repository_url}:${local.image_tag}"
  deployment_bundle_parameter_prefix = "/${var.project}/${var.environment}/deployments"
  azs                                = length(var.availability_zones) > 0 ? var.availability_zones : slice(data.aws_availability_zones.available.names, 0, var.az_count)

  # moimyeon DB URL contract is host:port/db (see STORAGE_DATABASE_CORE_DB_URL).
  db_url = "${aws_db_instance.core.address}:${aws_db_instance.core.port}/${var.db_name}"

  tg_name                = coalesce(var.target_group_name, "${local.name}-tg-app")
  ecs_blue_green_enabled = var.ecs_deployment_strategy == "BLUE_GREEN"
  alternate_tg_name      = substr("${local.name}-tg-alt", 0, 32)

  app_domain_enabled       = var.app_domain_name != null && var.app_domain_name != ""
  route53_zone_lookup      = var.route53_zone_name != null && var.route53_zone_name != ""
  route53_zone_id_provided = var.route53_zone_id != null && var.route53_zone_id != ""
  route53_dns_enabled      = local.app_domain_enabled && var.dns_management == "route53"
  route53_zone_lookup_needed = (
    local.route53_dns_enabled &&
    !local.route53_zone_id_provided &&
    local.route53_zone_lookup
  )
  external_dns_enabled = local.app_domain_enabled && var.dns_management == "external"
  certificate_enabled  = local.route53_dns_enabled || local.external_dns_enabled
  https_enabled        = local.certificate_enabled && var.enable_https

  upload_bucket_name = coalesce(
    var.upload_bucket_name,
    "${var.project}-${var.environment}-uploads",
  )

  alb_access_log_bucket_name = "${local.name}-alb-access-logs-${data.aws_caller_identity.current.account_id}"

  tags = merge(
    {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "terraform"
    },
    var.tags,
  )
}
