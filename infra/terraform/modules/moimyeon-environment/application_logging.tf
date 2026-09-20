variable "application_logging_mode" {
  description = "disabled: no logging resources; provision: retain destinations without routing; enabled: FireLens to S3 and CloudWatch."
  type        = string
  default     = "disabled"
  validation {
    condition     = contains(["disabled", "provision", "enabled"], var.application_logging_mode)
    error_message = "Application logging mode must be disabled, provision, or enabled."
  }
}

locals {
  log_routing_enabled = var.application_logging_mode == "enabled"
}

module "application_logging" {
  source      = "../application-logging"
  mode        = var.application_logging_mode
  name        = local.name
  environment = var.environment
  region      = data.aws_region.current.region
  account_id  = data.aws_caller_identity.current.account_id
  tags        = local.tags
  services = {
    api = {
      container_name      = var.container_name
      service_name        = "core-api"
      runtime_role_name   = aws_iam_role.task.name
      execution_role_name = aws_iam_role.task_execution.name
    }
    worker = {
      container_name      = var.notification_worker_container_name
      service_name        = "core-worker"
      runtime_role_name   = aws_iam_role.notification_worker.name
      execution_role_name = aws_iam_role.notification_worker_execution.name
    }
  }
}
