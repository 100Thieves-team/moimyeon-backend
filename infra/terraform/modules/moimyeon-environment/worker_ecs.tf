resource "aws_cloudwatch_log_group" "notification_worker" {
  name              = "/ecs/${local.name}/${var.notification_worker_container_name}"
  retention_in_days = var.log_retention_days

  tags = local.tags
}

locals {
  notification_worker_image_uri = "${aws_ecr_repository.notification_worker.repository_url}:${coalesce(var.notification_worker_image_tag, var.environment)}"

  notification_worker_environment_map = merge(
    {
      SPRING_PROFILES_ACTIVE            = local.profile
      STORAGE_DATABASE_CORE_DB_URL      = local.db_url
      STORAGE_DATABASE_CORE_DB_USERNAME = var.db_username
      AWS_REGION                        = data.aws_region.current.name
      AWS_DEFAULT_REGION                = data.aws_region.current.name
    },
    var.firebase_project_id == null ? {} : { FIREBASE_PROJECT_ID = var.firebase_project_id },
    var.notification_web_push_action_base_url == null ? {} : {
      NOTIFICATION_WEB_PUSH_ACTION_BASE_URL = var.notification_web_push_action_base_url
    },
    var.notification_email_ses_from_address == null ? {} : {
      NOTIFICATION_EMAIL_SES_FROM_ADDRESS = var.notification_email_ses_from_address
    },
    var.notification_email_gmail_address == null ? {} : {
      NOTIFICATION_EMAIL_GMAIL_ADDRESS = var.notification_email_gmail_address
    },
  )

  notification_worker_environment = [
    for name, value in local.notification_worker_environment_map : {
      name  = name
      value = value
    }
  ]
}

resource "aws_ecs_task_definition" "notification_worker" {
  family                   = "${local.name}-${var.notification_worker_container_name}"
  requires_compatibilities = ["EC2"]
  network_mode             = "awsvpc"
  cpu                      = tostring(var.notification_worker_task_cpu)
  memory                   = tostring(var.notification_worker_task_memory)
  execution_role_arn       = aws_iam_role.notification_worker_execution.arn
  task_role_arn            = aws_iam_role.notification_worker.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([
    {
      name        = var.notification_worker_container_name
      image       = local.notification_worker_image_uri
      essential   = true
      cpu         = var.notification_worker_task_cpu
      memory      = var.notification_worker_task_memory
      environment = local.notification_worker_environment
      secrets     = local.notification_worker_secrets

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.notification_worker.name
          awslogs-region        = data.aws_region.current.name
          awslogs-stream-prefix = var.notification_worker_container_name
        }
      }
    },
  ])

  tags = local.tags
}

resource "aws_ecs_service" "notification_worker" {
  name            = var.notification_worker_container_name
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.notification_worker.arn
  desired_count   = var.notification_worker_desired_count

  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 200
  enable_execute_command             = var.enable_ecs_exec

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  capacity_provider_strategy {
    capacity_provider = aws_ecs_capacity_provider.this.name
    weight            = 1
    base              = 0
  }

  network_configuration {
    subnets          = aws_subnet.private_app[*].id
    security_groups  = [aws_security_group.notification_worker_task.id]
    assign_public_ip = false
  }

  depends_on = [aws_ecs_cluster_capacity_providers.this]

  lifecycle {
    ignore_changes = [task_definition]

    precondition {
      condition = var.notification_worker_desired_count == 0 || (
        var.enable_notification_redis &&
        trimspace(coalesce(var.firebase_project_id, "")) != "" &&
        trimspace(coalesce(var.notification_web_push_action_base_url, "")) != "" &&
        trimspace(coalesce(var.notification_email_ses_from_address, "")) != "" &&
        trimspace(coalesce(var.notification_email_gmail_address, "")) != ""
      )
      error_message = "Starting core-worker requires notification Redis and all non-secret FCM/email settings."
    }
  }

  tags = local.tags
}
