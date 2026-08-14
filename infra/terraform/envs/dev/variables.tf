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
  default     = ["https://dev.moimyeon.plady.io", "http://localhost:3000", "http://localhost:5173"]

  validation {
    condition = contains(var.upload_cors_allowed_origins, "https://dev.moimyeon.plady.io") && alltrue([
      for origin in var.upload_cors_allowed_origins : contains([
        "https://dev.moimyeon.plady.io",
        "http://localhost:3000",
        "http://localhost:5173",
      ], origin)
    ])
    error_message = "Dev upload CORS must include the preview origin and only use approved preview or localhost origins."
  }
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
  description = "MySQL master username (existing dev RDS master user)."
  type        = string
  default     = "moimyeon_admin"
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

variable "notification_worker_desired_count" {
  description = "Desired core-worker count. Keep zero until vendor values and SSM secrets are ready."
  type        = number
  default     = 0
}

variable "firebase_project_id" {
  description = "Firebase project ID for notification web push."
  type        = string
  default     = null
}

variable "notification_web_push_action_base_url" {
  description = "Frontend base URL opened by web push notifications."
  type        = string
  default     = "https://dev.moimyeon.plady.io"

  validation {
    condition     = trimsuffix(var.notification_web_push_action_base_url, "/") == "https://dev.moimyeon.plady.io"
    error_message = "Dev web push notifications must open https://dev.moimyeon.plady.io."
  }
}

variable "notification_email_ses_from_address" {
  description = "Verified SES sender address."
  type        = string
  default     = null
}

variable "notification_email_gmail_address" {
  description = "Gmail or Google Workspace fallback sender address."
  type        = string
  default     = null
}

variable "tags" {
  description = "Extra tags."
  type        = map(string)
  default     = {}
}
