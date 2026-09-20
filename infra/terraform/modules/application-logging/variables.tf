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
  }))
}
