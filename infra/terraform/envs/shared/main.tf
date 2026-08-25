module "foundation" {
  source = "../../modules/shared-foundation"

  project                             = var.project
  github_repository                   = var.github_repository
  github_immutable_repository         = var.github_immutable_repository
  create_oidc_provider                = var.create_oidc_provider
  terraform_state_bucket_name         = var.terraform_state_bucket_name
  terraform_lock_table_name           = var.terraform_lock_table_name
  terraform_plan_artifact_bucket_name = var.terraform_plan_artifact_bucket_name
  route53_zone_name                   = var.route53_zone_name
  create_hosted_zone                  = var.create_hosted_zone
  register_domain                     = var.register_domain
  domain_contact                      = var.domain_contact
  tags                                = var.tags
}
