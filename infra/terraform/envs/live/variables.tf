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

variable "promotion_source_api_ecr_repository_name" {
  description = "Dev Core API ECR repository read by the live promotion role."
  type        = string
  default     = "moimyeon/backend"
}

variable "promotion_source_worker_ecr_repository_name" {
  description = "Dev Worker ECR repository read by the live promotion role."
  type        = string
  default     = "moimyeon/worker"
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
  description = "API domain for live, e.g. api.moimyeon.plady.io."
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
  description = "Frontend origins allowed to use S3 presigned upload/download URLs (live)."
  type        = list(string)
  default     = []
}

variable "vpc_cidr" {
  description = "Live VPC CIDR."
  type        = string
  default     = "10.30.0.0/16"
}

variable "db_name" {
  description = "MySQL database name."
  type        = string
  default     = "moimyeon"
}

variable "db_username" {
  description = "Least-privilege application MySQL username provisioned during bootstrap."
  type        = string
  default     = "moimyeon"
}

variable "db_master_username" {
  description = "RDS admin username whose password is managed in Secrets Manager."
  type        = string
  default     = "moimyeon_admin"
}

variable "oauth_google_client_id" {
  description = "Google OAuth client ID. May be null only while the API is scaled to zero."
  type        = string
  default     = null
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
  default     = null
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
