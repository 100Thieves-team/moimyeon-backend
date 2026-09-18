variable "project" {
  description = "Project slug used in shared resource names and tags."
  type        = string
  default     = "moimyeon"
}

variable "github_repository" {
  description = "GitHub repository allowed to assume deployment roles, in owner/repo form."
  type        = string
}

variable "github_immutable_repository" {
  description = "GitHub immutable OIDC subject prefix in org@id/repo@id form. Null trusts only the legacy owner/repo subject."
  type        = string
  default     = null
}

variable "github_repository_id" {
  description = "Immutable numeric GitHub repository ID used in OIDC trust conditions."
  type        = string
}

variable "github_repository_owner_id" {
  description = "Immutable numeric GitHub organization ID used in OIDC trust conditions."
  type        = string
}

variable "terraform_state_bucket_name" {
  description = "Existing Terraform remote-state bucket. Null derives <project>-terraform-state-<account>-<region>."
  type        = string
  default     = null
}

variable "terraform_lock_table_name" {
  description = "Existing DynamoDB table used by the Terraform S3 backend for state locking."
  type        = string
  default     = "moimyeon-terraform-locks"
}

variable "terraform_plan_artifact_bucket_name" {
  description = "Private bucket for short-lived exact Terraform plans. Null derives <project>-terraform-plans-<account>-<region>."
  type        = string
  default     = null
}

variable "create_oidc_provider" {
  description = "Create the GitHub Actions OIDC provider. Set false when the account already has one (e.g. 781897847312 already does) and reference it instead."
  type        = bool
  default     = true
}

variable "route53_zone_name" {
  description = "Root public DNS zone name, e.g. moimyeon.plady.io. Null skips Route 53."
  type        = string
  default     = null
}

variable "create_hosted_zone" {
  description = "Create a public Route 53 hosted zone. Set false when DNS is managed outside Route 53 (e.g. Cloudflare)."
  type        = bool
  default     = true
}

variable "register_domain" {
  description = "Register route53_zone_name through Route 53 Domains. This purchases/renews a real domain."
  type        = bool
  default     = false
}

variable "domain_auto_renew" {
  description = "Enable automatic renewal for a domain registered by this module."
  type        = bool
  default     = true
}

variable "domain_duration_in_years" {
  description = "Registration duration for a domain registered by this module."
  type        = number
  default     = 1
}

variable "domain_privacy_protection" {
  description = "Apply the same WHOIS privacy setting to admin, billing, registrant, and tech contacts."
  type        = bool
  default     = true
}

variable "domain_contact" {
  description = "Contact details required only when register_domain is true. This is personal data."
  type = object({
    address_line_1    = string
    address_line_2    = optional(string)
    city              = string
    contact_type      = optional(string, "PERSON")
    country_code      = string
    email             = string
    fax               = optional(string)
    first_name        = string
    last_name         = string
    organization_name = optional(string)
    phone_number      = string
    state             = optional(string)
    zip_code          = string
  })
  default   = null
  sensitive = true
}

variable "tags" {
  description = "Extra tags applied to shared resources."
  type        = map(string)
  default     = {}
}
