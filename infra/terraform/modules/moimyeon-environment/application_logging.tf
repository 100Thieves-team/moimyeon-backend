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
  logging_task_inputs = {
    api = {
      cpu            = var.task_cpu
      memory         = var.task_memory
      container_name = var.container_name
      legacy_group   = aws_cloudwatch_log_group.app.name
    }
    worker = {
      cpu            = var.notification_worker_task_cpu
      memory         = var.notification_worker_task_memory
      container_name = var.notification_worker_container_name
      legacy_group   = aws_cloudwatch_log_group.notification_worker.name
    }
  }

  # One policy owns how every task consumes the router's resource and routing contract.
  logging_task_policy = {
    for key, task in local.logging_task_inputs : key => {
      memory           = task.memory + (local.log_routing_enabled ? module.application_logging.extra_memory : 0)
      cpu_budget_valid = !local.log_routing_enabled || task.cpu > module.application_logging.router_cpu
      containers       = local.log_routing_enabled ? [module.application_logging.routers[key]] : []
      volumes          = local.log_routing_enabled ? [{ name = "log-router-buffer" }] : []
      app_settings = merge(
        {
          cpu = task.cpu - (local.log_routing_enabled ? module.application_logging.router_cpu : 0)
          logConfiguration = local.log_routing_enabled ? module.application_logging.app_log_configurations[key] : var.application_logging_mode == "provision" ? module.application_logging.fallback_log_configurations[key] : {
            logDriver = "awslogs"
            options = {
              awslogs-group         = task.legacy_group
              awslogs-region        = data.aws_region.current.region
              awslogs-stream-prefix = task.container_name
            }
          }
        },
        local.log_routing_enabled ? { dependsOn = [{ containerName = "log-router", condition = "HEALTHY" }] } : {},
      )
    }
  }
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
