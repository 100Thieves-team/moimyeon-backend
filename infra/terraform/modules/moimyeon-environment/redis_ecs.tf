resource "random_password" "notification_redis_auth" {
  count = var.enable_notification_redis ? 1 : 0

  length      = 48
  special     = false
  min_lower   = 2
  min_numeric = 2
  min_upper   = 2
}

resource "aws_efs_file_system" "notification_redis" {
  count = var.enable_notification_redis ? 1 : 0

  encrypted        = true
  performance_mode = "generalPurpose"
  throughput_mode  = "bursting"

  tags = merge(local.tags, {
    Name = "${local.name}-notification-redis"
  })
}

resource "aws_efs_backup_policy" "notification_redis" {
  count = var.enable_notification_redis ? 1 : 0

  file_system_id = aws_efs_file_system.notification_redis[0].id

  backup_policy {
    status = "ENABLED"
  }
}

resource "aws_efs_access_point" "notification_redis" {
  count = var.enable_notification_redis ? 1 : 0

  file_system_id = aws_efs_file_system.notification_redis[0].id

  posix_user {
    uid = 999
    gid = 1000
  }

  root_directory {
    path = "/redis"

    creation_info {
      owner_uid   = 999
      owner_gid   = 1000
      permissions = "0770"
    }
  }

  tags = local.tags
}

resource "aws_efs_mount_target" "notification_redis" {
  count = var.enable_notification_redis ? length(aws_subnet.private_app) : 0

  file_system_id  = aws_efs_file_system.notification_redis[0].id
  subnet_id       = aws_subnet.private_app[count.index].id
  security_groups = [aws_security_group.notification_redis_efs[0].id]
}

resource "aws_service_discovery_private_dns_namespace" "notification" {
  count = var.enable_notification_redis ? 1 : 0

  name        = "${local.name}.internal"
  description = "Private ECS service discovery for ${local.name}"
  vpc         = aws_vpc.this.id

  tags = local.tags
}

resource "aws_service_discovery_service" "notification_redis" {
  count = var.enable_notification_redis ? 1 : 0

  name = "notification-redis"

  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.notification[0].id
    routing_policy = "MULTIVALUE"

    dns_records {
      ttl  = 10
      type = "A"
    }
  }

  health_check_custom_config {
    failure_threshold = 1
  }

  tags = local.tags
}

resource "aws_cloudwatch_log_group" "notification_redis" {
  count = var.enable_notification_redis ? 1 : 0

  name              = "/ecs/${local.name}/notification-redis"
  retention_in_days = var.log_retention_days

  tags = local.tags
}

locals {
  notification_redis_host          = "notification-redis.${local.name}.internal"
  notification_redis_start_command = <<-EOT
    set -eu
    umask 077
    cat > /tmp/redis.conf <<EOF
    appendonly yes
    appendfsync always
    aof-use-rdb-preamble yes
    dir /data
    maxmemory ${floor(var.notification_redis_task_memory * 0.6)}mb
    maxmemory-policy noeviction
    requirepass $REDIS_PASSWORD
    EOF
    exec redis-server /tmp/redis.conf
  EOT
}

resource "aws_ecs_task_definition" "notification_redis" {
  count = var.enable_notification_redis ? 1 : 0

  family                   = "${local.name}-notification-redis"
  requires_compatibilities = ["EC2"]
  network_mode             = "awsvpc"
  cpu                      = tostring(var.notification_redis_task_cpu)
  memory                   = tostring(var.notification_redis_task_memory)
  execution_role_arn       = aws_iam_role.notification_redis_execution[0].arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  volume {
    name = "redis-data"

    efs_volume_configuration {
      file_system_id     = aws_efs_file_system.notification_redis[0].id
      transit_encryption = "ENABLED"
      root_directory     = "/"

      authorization_config {
        access_point_id = aws_efs_access_point.notification_redis[0].id
        iam             = "DISABLED"
      }
    }
  }

  container_definitions = jsonencode([
    {
      name      = "notification-redis"
      image     = var.notification_redis_image
      essential = true
      cpu       = var.notification_redis_task_cpu
      memory    = var.notification_redis_task_memory
      user      = "999:1000"

      portMappings = [
        {
          name          = "redis"
          containerPort = 6379
          hostPort      = 6379
          protocol      = "tcp"
        },
      ]

      secrets = [
        {
          name      = "REDIS_PASSWORD"
          valueFrom = aws_ssm_parameter.notification_redis_password[0].arn
        },
      ]

      command = [
        "sh",
        "-c",
        local.notification_redis_start_command,
      ]

      mountPoints = [
        {
          sourceVolume  = "redis-data"
          containerPath = "/data"
          readOnly      = false
        },
      ]

      healthCheck = {
        command = [
          "CMD-SHELL",
          "REDISCLI_AUTH=\"$REDIS_PASSWORD\" redis-cli ping | grep -q PONG",
        ]
        interval    = 15
        timeout     = 5
        retries     = 3
        startPeriod = 20
      }

      stopTimeout = 60

      ulimits = [
        {
          name      = "nofile"
          softLimit = 65535
          hardLimit = 65535
        },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.notification_redis[0].name
          awslogs-region        = data.aws_region.current.name
          awslogs-stream-prefix = "notification-redis"
        }
      }
    },
  ])

  tags = local.tags
}

resource "aws_ecs_service" "notification_redis" {
  count = var.enable_notification_redis ? 1 : 0

  name            = "notification-redis"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.notification_redis[0].arn
  desired_count   = 1

  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100
  enable_execute_command             = false

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  capacity_provider_strategy {
    capacity_provider = aws_ecs_capacity_provider.this.name
    weight            = 1
    base              = 1
  }

  network_configuration {
    subnets          = aws_subnet.private_app[*].id
    security_groups  = [aws_security_group.notification_redis[0].id]
    assign_public_ip = false
  }

  service_registries {
    registry_arn = aws_service_discovery_service.notification_redis[0].arn
  }

  depends_on = [
    aws_ecs_cluster_capacity_providers.this,
    aws_efs_mount_target.notification_redis,
  ]

  lifecycle {
    precondition {
      condition     = var.ecs_max_size > 0
      error_message = "Notification Redis on ECS requires non-zero ECS EC2 capacity."
    }
  }

  tags = local.tags
}

resource "aws_ssm_parameter" "notification_redis_password" {
  count = var.enable_notification_redis ? 1 : 0

  name        = "/${var.project}/${var.environment}/notification-redis/PASSWORD"
  description = "Authentication password for the notification Redis ECS service in ${local.name}"
  type        = "SecureString"
  value       = random_password.notification_redis_auth[0].result

  tags = local.tags
}

resource "aws_ssm_parameter" "notification_redis_url" {
  count = var.enable_notification_redis ? 1 : 0

  name        = "/${var.project}/${var.environment}/shared/STORAGE_REDIS_URL"
  description = "Private notification Redis URL shared by core-api and core-worker in ${local.name}"
  type        = "SecureString"
  value       = "redis://:${random_password.notification_redis_auth[0].result}@${local.notification_redis_host}:6379"

  tags = local.tags
}
