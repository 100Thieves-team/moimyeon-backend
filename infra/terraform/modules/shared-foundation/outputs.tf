output "github_oidc_provider_arn" {
  description = "GitHub Actions OIDC provider ARN (created or looked up)."
  value       = local.github_oidc_provider_arn
}

output "terraform_plan_artifact_bucket_name" {
  description = "Private S3 bucket containing short-lived exact Terraform plans."
  value       = aws_s3_bucket.terraform_plan_artifacts.id
}

output "terraform_plan_kms_key_arn" {
  description = "KMS key used for exact Terraform plan artifacts."
  value       = aws_kms_key.terraform_plan_artifacts.arn
}

output "terraform_review_plan_role_arn" {
  description = "OIDC role allowed to write only plans/pr artifacts."
  value       = aws_iam_role.terraform_plan["terraform-review-plan"].arn
}

output "terraform_drift_plan_role_arn" {
  description = "OIDC role allowed to write only plans/drift artifacts."
  value       = aws_iam_role.terraform_plan["terraform-drift-plan"].arn
}

output "terraform_apply_plan_role_arn" {
  description = "OIDC role allowed to write only trusted plans/apply artifacts."
  value       = aws_iam_role.terraform_plan["terraform-apply-plan"].arn
}

output "terraform_apply_role_arns" {
  description = "Environment-scoped OIDC roles that read and apply approved exact plans."
  value       = { for environment, role in aws_iam_role.terraform_apply : environment => role.arn }
}

output "route53_zone_id" {
  description = "Public Route 53 hosted zone ID, if enabled."
  value       = local.route53_zone_id
}

output "route53_zone_name" {
  description = "Public Route 53 hosted zone name, if enabled."
  value       = var.route53_zone_name
}

output "route53_name_servers" {
  description = "Hosted zone name servers to use for domain delegation."
  value       = local.route53_name_servers
}
