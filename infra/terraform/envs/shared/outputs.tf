output "github_oidc_provider_arn" {
  description = "GitHub Actions OIDC provider ARN. Environment roots resolve the same provider with an AWS data source."
  value       = module.foundation.github_oidc_provider_arn
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
