output "github_oidc_provider_arn" {
  description = "GitHub Actions OIDC provider ARN (created or looked up)."
  value       = local.github_oidc_provider_arn
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
