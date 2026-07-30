variable "aws_region" {
  description = "AWS region."
  type        = string
  default     = "ap-northeast-2"
}

variable "project" {
  description = "Project slug."
  type        = string
  default     = "moimyeon"
}

variable "github_repository" {
  description = "GitHub repository in owner/repo form."
  type        = string
  default     = "100Thieves-team/moimyeon-backend"
}

variable "github_oidc_provider_arn" {
  description = "Shared GitHub Actions OIDC provider ARN (from envs/shared output, or the existing account provider)."
  type        = string
}

variable "route53_zone_id" {
  description = "Route 53 hosted zone ID. Leave null for external (Cloudflare) DNS."
  type        = string
  default     = null
}

variable "route53_zone_name" {
  description = "Route 53 hosted zone name, used only when route53_zone_id is not set."
  type        = string
  default     = null
}

variable "app_domain_name" {
  description = "API domain for dev, e.g. api.dev.moimyeon.plady.io."
  type        = string
  default     = null
}

variable "dns_management" {
  description = "DNS mode: external for Cloudflare/manual DNS, route53 for Terraform-managed, none for ALB DNS/HTTP only."
  type        = string
  default     = "external"
}

variable "enable_https" {
  description = "Create the HTTPS listener after the external ACM validation record exists."
  type        = bool
  default     = false
}

variable "upload_cors_allowed_origins" {
  description = "Frontend origins allowed to use S3 presigned upload/download URLs (dev)."
  type        = list(string)
  default     = ["http://localhost:5173"]
}

variable "vpc_cidr" {
  description = "Dev VPC CIDR."
  type        = string
  default     = "10.20.0.0/16"
}

variable "db_name" {
  description = "MySQL database name."
  type        = string
  default     = "moimyeondev"
}

variable "db_username" {
  description = "MySQL master username."
  type        = string
  default     = "moimyeon"
}

variable "oauth_google_client_id" {
  description = "Google OAuth client ID (required to boot)."
  type        = string
}

variable "oauth_google_client_secret" {
  description = "Google OAuth client secret (required to boot)."
  type        = string
  sensitive   = true
}

variable "tags" {
  description = "Extra tags."
  type        = map(string)
  default     = {}
}
