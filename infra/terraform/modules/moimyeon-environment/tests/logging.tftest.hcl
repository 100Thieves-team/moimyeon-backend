# Plan the actual consumer resources with no AWS calls or state access.
mock_provider "aws" {
  override_during = plan
  mock_resource "aws_ecr_repository" {
    defaults = { repository_url = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/test" }
  }
  mock_resource "aws_db_instance" {
    defaults = { address = "db.example.invalid" }
  }
  mock_resource "aws_ssm_parameter" {
    defaults = { arn = "arn:aws:ssm:ap-northeast-2:123456789012:parameter/test" }
  }
  mock_data "aws_caller_identity" {
    defaults = { account_id = "123456789012" }
  }
  mock_data "aws_region" {
    defaults = { region = "ap-northeast-2", name = "ap-northeast-2" }
  }
  mock_data "aws_availability_zones" {
    defaults = { names = ["ap-northeast-2a", "ap-northeast-2c"] }
  }
  mock_data "aws_iam_policy_document" {
    defaults = { json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}" }
  }
  mock_data "aws_ssm_parameter" {
    defaults = { value = "ami-0123456789abcdef0" }
  }
}
mock_provider "random" {}

# Test the consumer contract with a deliberately different router budget.
# The producer module has its own real configuration tests; mocking its outputs
# also avoids apply-only dependencies on the role policies it provisions.
override_module {
  target = module.application_logging
  outputs = {
    router_cpu   = 96
    extra_memory = 224
    bucket_name  = "test-logs"
    app_log_configurations = {
      api    = { logDriver = "awsfirelens", options = { Name = "null", mode = "non-blocking", marker = "api" } }
      worker = { logDriver = "awsfirelens", options = { Name = "null", mode = "non-blocking", marker = "worker" } }
    }
    fallback_log_configurations = {
      api    = { logDriver = "awslogs", options = { awslogs-group = "test-api-fallback", mode = "non-blocking" } }
      worker = { logDriver = "awslogs", options = { awslogs-group = "test-worker-fallback", mode = "non-blocking" } }
    }
    routers = {
      api = {
        name        = "log-router", image = "example/router:test", cpu = 96, memory = 192
        mountPoints = [{ sourceVolume = "log-router-buffer", containerPath = "/buffers", readOnly = false }]
      }
      worker = {
        name        = "log-router", image = "example/router:test", cpu = 96, memory = 192
        mountPoints = [{ sourceVolume = "log-router-buffer", containerPath = "/buffers", readOnly = false }]
      }
    }
  }
}

variables {
  environment                     = "dev"
  github_repository               = "example/backend"
  github_branch                   = "dev"
  github_oidc_provider_arn        = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
  github_repository_id            = "123"
  github_repository_owner_id      = "456"
  github_deploy_workflows         = ["Deploy"]
  github_deploy_execution_refs    = ["refs/heads/dev"]
  oauth_google_client_id          = "synthetic-client-id"
  task_cpu                        = 1024
  task_memory                     = 1408
  notification_worker_task_cpu    = 384
  notification_worker_task_memory = 512
}

run "enabled_task_budgets_and_wiring" {
  command = plan
  variables { application_logging_mode = "enabled" }
  assert {
    condition = alltrue([for task in [aws_ecs_task_definition.app, aws_ecs_task_definition.notification_worker] :
      length(jsondecode(task.container_definitions)) == 2 &&
      sum([for container in jsondecode(task.container_definitions) : container.cpu]) == tonumber(task.cpu) &&
      sum([for container in jsondecode(task.container_definitions) : container.memory]) + 32 == tonumber(task.memory)
    ])
    error_message = "Both tasks must fit app + router CPU and preserve the driver memory allowance."
  }
  assert {
    condition     = jsondecode(aws_ecs_task_definition.app.container_definitions)[0].memory == 1408 && jsondecode(aws_ecs_task_definition.notification_worker.container_definitions)[0].memory == 512
    error_message = "Routing must not reduce either application's memory budget."
  }
  assert {
    condition = alltrue([for task in [aws_ecs_task_definition.app, aws_ecs_task_definition.notification_worker] :
      jsondecode(task.container_definitions)[0].logConfiguration.logDriver == "awsfirelens" &&
      jsondecode(task.container_definitions)[0].dependsOn[0].containerName == "log-router" &&
      jsondecode(task.container_definitions)[0].dependsOn[0].condition == "HEALTHY" &&
      jsondecode(task.container_definitions)[1].name == "log-router" &&
      jsondecode(task.container_definitions)[1].mountPoints[0].sourceVolume == one(task.volume).name &&
      one(task.volume).name == "log-router-buffer"
    ])
    error_message = "Both task definitions must connect the app, healthy router, and buffer volume."
  }
  assert {
    condition     = jsondecode(aws_ecs_task_definition.app.container_definitions)[0].logConfiguration.options.marker == "api" && jsondecode(aws_ecs_task_definition.notification_worker.container_definitions)[0].logConfiguration.options.marker == "worker"
    error_message = "API and worker must consume their own service's routing configuration."
  }

}

run "provision_uses_nonblocking_fallback_without_router" {
  command = plan
  variables { application_logging_mode = "provision" }
  assert {
    condition = alltrue([for task in [aws_ecs_task_definition.app, aws_ecs_task_definition.notification_worker] :
      length(jsondecode(task.container_definitions)) == 1 && length(task.volume) == 0 &&
      !can(jsondecode(task.container_definitions)[0].dependsOn) &&
      jsondecode(task.container_definitions)[0].cpu == tonumber(task.cpu) &&
      jsondecode(task.container_definitions)[0].memory == tonumber(task.memory) &&
      jsondecode(task.container_definitions)[0].logConfiguration.logDriver == "awslogs" &&
      jsondecode(task.container_definitions)[0].logConfiguration.options.mode == "non-blocking"
    ])
    error_message = "Provision must preserve app resources and use nonblocking awslogs without a router."
  }
  assert {
    condition     = jsondecode(aws_ecs_task_definition.app.container_definitions)[0].logConfiguration.options.awslogs-group == "test-api-fallback" && jsondecode(aws_ecs_task_definition.notification_worker.container_definitions)[0].logConfiguration.options.awslogs-group == "test-worker-fallback"
    error_message = "Each service must select its own fallback group, not the legacy group."
  }
}

run "disabled_preserves_legacy_tasks" {
  command = plan
  variables { application_logging_mode = "disabled" }
  assert {
    condition = alltrue([for task in [aws_ecs_task_definition.app, aws_ecs_task_definition.notification_worker] :
      length(jsondecode(task.container_definitions)) == 1 && length(task.volume) == 0 &&
      !can(jsondecode(task.container_definitions)[0].dependsOn) &&
      jsondecode(task.container_definitions)[0].cpu == tonumber(task.cpu) &&
      jsondecode(task.container_definitions)[0].memory == tonumber(task.memory) &&
      jsondecode(task.container_definitions)[0].logConfiguration.logDriver == "awslogs"
    ])
    error_message = "Disabled must preserve the original single-container budgets and logging."
  }
  assert {
    condition     = jsondecode(aws_ecs_task_definition.app.container_definitions)[0].logConfiguration.options.awslogs-group == aws_cloudwatch_log_group.app.name && jsondecode(aws_ecs_task_definition.notification_worker.container_definitions)[0].logConfiguration.options.awslogs-group == aws_cloudwatch_log_group.notification_worker.name
    error_message = "Each disabled service must retain its original log group."
  }
}

run "api_cpu_equal_to_router_is_rejected" {
  command = plan
  variables {
    application_logging_mode = "enabled"
    task_cpu                 = 96
  }
  expect_failures = [aws_ecs_task_definition.app]
}

run "worker_cpu_below_router_is_rejected" {
  command = plan
  variables {
    application_logging_mode     = "enabled"
    notification_worker_task_cpu = 32
  }
  expect_failures = [aws_ecs_task_definition.notification_worker]
}

run "provision_does_not_require_router_cpu" {
  command = plan
  variables {
    application_logging_mode     = "provision"
    task_cpu                     = 32
    notification_worker_task_cpu = 32
  }
  assert {
    condition     = jsondecode(aws_ecs_task_definition.app.container_definitions)[0].cpu == 32 && jsondecode(aws_ecs_task_definition.notification_worker.container_definitions)[0].cpu == 32
    error_message = "Router CPU must not be reserved when provision removes the router."
  }
}
