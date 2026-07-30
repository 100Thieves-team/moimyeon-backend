module "foundation" {
  source = "../../modules/shared-foundation"

  project              = var.project
  github_repository    = var.github_repository
  create_oidc_provider = var.create_oidc_provider
  route53_zone_name    = var.route53_zone_name
  create_hosted_zone   = var.create_hosted_zone
  register_domain      = var.register_domain
  domain_contact       = var.domain_contact
  tags                 = var.tags
}
