variable "mode" {
  type    = string
  default = "disabled"
  validation {
    condition     = contains(["disabled", "provision", "enabled"], var.mode)
    error_message = "Logging mode must be disabled, provision, or enabled. Use provision to stop routing without deleting retained archives."
  }
}

variable "name" { type = string }
variable "environment" { type = string }
variable "region" { type = string }
variable "account_id" { type = string }
variable "tags" {
  type    = map(string)
  default = {}
}
variable "services" {
  type = map(object({
    container_name      = string
    service_name        = string
    runtime_role_name   = string
    execution_role_name = string
    # Send this service's growth records to PostHog. Requires var.posthog.
    posthog_enabled = optional(bool, false)
  }))
  validation {
    condition     = var.posthog != null || alltrue([for service in values(var.services) : !service.posthog_enabled])
    error_message = "posthog must be set when a service enables PostHog delivery."
  }
}

# The router reads the project token from a pre-created SSM SecureString through
# the task execution role. Terraform only receives its ARN.
variable "posthog" {
  type = object({
    host                        = optional(string, "us.i.posthog.com")
    project_token_parameter_arn = string
  })
  default = null
}
