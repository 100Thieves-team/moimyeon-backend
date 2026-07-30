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

variable "create_oidc_provider" {
  description = "Create the GitHub Actions OIDC provider. Account 781897847312 already has one, so default false and reference it."
  type        = bool
  default     = false
}

variable "route53_zone_name" {
  description = "Root public DNS zone name. Null / create_hosted_zone=false when DNS is managed in Cloudflare."
  type        = string
  default     = null
}

variable "create_hosted_zone" {
  description = "Create a public Route 53 hosted zone."
  type        = bool
  default     = false
}

variable "register_domain" {
  description = "Register route53_zone_name through Route 53 Domains."
  type        = bool
  default     = false
}

variable "domain_contact" {
  description = "Contact details required only when register_domain is true."
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
  description = "Extra tags."
  type        = map(string)
  default     = {}
}
