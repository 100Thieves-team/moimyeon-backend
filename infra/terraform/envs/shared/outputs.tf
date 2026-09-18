output "github_oidc_provider_arn" {
  description = "GitHub Actions OIDC provider ARN. Environment roots resolve the same provider with an AWS data source."
  value       = module.foundation.github_oidc_provider_arn
}

output "terraform_plan_artifact_bucket_name" {
  description = "Private S3 bucket containing short-lived exact Terraform plans."
  value       = module.foundation.terraform_plan_artifact_bucket_name
}

output "terraform_plan_kms_key_arn" {
  description = "KMS key used for exact Terraform plan artifacts."
  value       = module.foundation.terraform_plan_kms_key_arn
}

output "terraform_review_plan_role_arn" {
  description = "OIDC role for approved internal PR plans."
  value       = module.foundation.terraform_review_plan_role_arn
}

output "terraform_drift_plan_role_arn" {
  description = "OIDC role for scheduled drift plans."
  value       = module.foundation.terraform_drift_plan_role_arn
}

output "terraform_apply_plan_role_arn" {
  description = "OIDC role for trusted merged-SHA exact plans."
  value       = module.foundation.terraform_apply_plan_role_arn
}

output "terraform_apply_role_arns" {
  description = "Environment-scoped OIDC roles for approved exact-plan apply."
  value       = module.foundation.terraform_apply_role_arns
}

output "route53_zone_id" {
  description = "Public Route 53 hosted zone ID, if enabled."
  value       = module.foundation.route53_zone_id
}

output "route53_zone_name" {
  description = "Public Route 53 hosted zone name, if enabled."
  value       = module.foundation.route53_zone_name
}

output "route53_name_servers" {
  description = "Hosted zone name servers."
  value       = module.foundation.route53_name_servers
}
