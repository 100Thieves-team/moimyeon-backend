variable "aws_region" {
  description = "Region used for the AWS provider. Organizations itself is global."
  type        = string
  default     = "ap-northeast-2"
}

variable "management_account_id" {
  description = "Expected personal AWS Organizations management account ID. Never use the team account ID."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.management_account_id))
    error_message = "management_account_id must be a 12-digit AWS account ID."
  }
}

variable "enabled_policy_types" {
  description = "Organization policy types. Include existing types before importing an Organization."
  type        = set(string)
  default     = ["SERVICE_CONTROL_POLICY"]

  validation {
    condition     = contains(var.enabled_policy_types, "SERVICE_CONTROL_POLICY")
    error_message = "enabled_policy_types must include SERVICE_CONTROL_POLICY."
  }
}

variable "aws_service_access_principals" {
  description = "Trusted AWS service principals. Include all existing values before importing an Organization."
  type        = set(string)
  default     = []
}

variable "ou_names" {
  description = "Empty workload OUs prepared for future member accounts."
  type        = set(string)
  default     = ["dev", "live"]

  validation {
    condition     = length(var.ou_names) > 0 && alltrue([for name in var.ou_names : length(trimspace(name)) > 0])
    error_message = "ou_names must contain at least one non-empty name."
  }
}

variable "allowed_regions" {
  description = "Regions permitted by the optional SCP for regional API requests."
  type        = set(string)
  default     = ["ap-northeast-2", "us-east-1"]

  validation {
    condition     = length(var.allowed_regions) > 0
    error_message = "allowed_regions must contain at least one Region."
  }
}

variable "attach_guardrails" {
  description = "Attach the SCPs to workload OUs only after testing with a member account."
  type        = bool
  default     = false
}
