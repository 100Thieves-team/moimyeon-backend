data "aws_ssm_parameter" "ecs_optimized_ami" {
  name = "/aws/service/ecs/optimized-ami/amazon-linux-2023/recommended/image_id"
}

resource "aws_ecs_cluster" "this" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = local.tags
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${local.name}/${var.container_name}"
  retention_in_days = var.log_retention_days

  tags = local.tags
}

locals {
  # Non-sensitive container env. Secrets (DB password, JWT, OAuth secret) are
  # injected separately via local.container_secrets (secrets.tf).
  container_environment_map = merge(
    {
      SPRING_PROFILES_ACTIVE            = local.profile
      SERVER_PORT                       = tostring(var.container_port)
      STORAGE_DATABASE_CORE_DB_URL      = local.db_url
      STORAGE_DATABASE_CORE_DB_USERNAME = var.db_username
      GOOGLE_OAUTH_CLIENT_ID            = var.oauth_google_client_id
      AWS_REGION                        = data.aws_region.current.name
      AWS_DEFAULT_REGION                = data.aws_region.current.name
    },
    var.jwt_issuer == null ? {} : { JWT_ISSUER = var.jwt_issuer },
    var.additional_environment,
  )

  container_environment = [
    for name, value in local.container_environment_map : {
      name  = name
      value = value
    }
  ]

  container_health_check = var.enable_container_health_check ? {
    healthCheck = {
      command = [
        "CMD-SHELL",
        "wget -q -O /dev/null http://127.0.0.1:${var.container_port}${var.health_check_path} || exit 1",
      ]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = var.container_health_check_start_period_seconds
    }
  } : {}

  container_definition = merge(
    {
      name      = var.container_name
      image     = local.image_uri
      essential = true
      cpu       = var.task_cpu
      memory    = var.task_memory

      portMappings = [
        {
          containerPort = var.container_port
          hostPort      = var.container_port
          protocol      = "tcp"
        },
      ]

      environment = local.container_environment
      secrets     = local.container_secrets

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.app.name
          awslogs-region        = data.aws_region.current.name
          awslogs-stream-prefix = var.container_name
        }
      }
    },
    local.container_health_check,
  )
}

resource "aws_ecs_task_definition" "app" {
  family                   = "${local.name}-${var.container_name}"
  requires_compatibilities = ["EC2"]
  network_mode             = "awsvpc"
  cpu                      = tostring(var.task_cpu)
  memory                   = tostring(var.task_memory)
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([local.container_definition])

  tags = local.tags
}

resource "aws_launch_template" "ecs" {
  name_prefix            = "${local.name}-ecs-"
  image_id               = data.aws_ssm_parameter.ecs_optimized_ami.value
  instance_type          = var.ecs_instance_type
  update_default_version = true
  vpc_security_group_ids = [aws_security_group.ecs_instance.id]

  iam_instance_profile {
    name = aws_iam_instance_profile.ecs_instance.name
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      volume_size           = var.ecs_instance_root_volume_size
      volume_type           = "gp3"
      encrypted             = true
      delete_on_termination = true
    }
  }

  user_data = base64encode(<<-EOF
    #!/bin/bash
    cat >/etc/ecs/ecs.config <<'ECS'
    ECS_CLUSTER=${aws_ecs_cluster.this.name}
    ECS_ENABLE_CONTAINER_METADATA=true
    ECS_ENABLE_TASK_IAM_ROLE=true
    ECS_AWSVPC_BLOCK_IMDS=true
    ECS
  EOF
  )

  tag_specifications {
    resource_type = "instance"

    tags = merge(local.tags, {
      Name = "${local.name}-ecs"
    })
  }

  tags = local.tags
}

resource "aws_autoscaling_group" "ecs" {
  name                = "${local.name}-ecs"
  min_size            = var.ecs_min_size
  max_size            = var.ecs_max_size
  desired_capacity    = var.ecs_desired_capacity
  vpc_zone_identifier = aws_subnet.private_app[*].id
  health_check_type   = "EC2"

  launch_template {
    id      = aws_launch_template.ecs.id
    version = "$Latest"
  }

  tag {
    key                 = "Name"
    value               = "${local.name}-ecs"
    propagate_at_launch = true
  }

  tag {
    key                 = "AmazonECSManaged"
    value               = "true"
    propagate_at_launch = true
  }

  dynamic "tag" {
    for_each = local.tags

    content {
      key                 = tag.key
      value               = tag.value
      propagate_at_launch = true
    }
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_ecs_capacity_provider" "this" {
  name = "${local.name}-capacity"

  auto_scaling_group_provider {
    auto_scaling_group_arn         = aws_autoscaling_group.ecs.arn
    managed_termination_protection = "DISABLED"

    managed_scaling {
      status                    = "ENABLED"
      target_capacity           = 80
      minimum_scaling_step_size = 1
      maximum_scaling_step_size = 2
    }
  }

  tags = local.tags
}

resource "aws_ecs_cluster_capacity_providers" "this" {
  cluster_name       = aws_ecs_cluster.this.name
  capacity_providers = [aws_ecs_capacity_provider.this.name]

  default_capacity_provider_strategy {
    capacity_provider = aws_ecs_capacity_provider.this.name
    weight            = 1
    base              = 1
  }
}

resource "aws_ecs_service" "app" {
  name            = var.container_name
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.ecs_service_desired_count

  deployment_minimum_healthy_percent = 50
  deployment_maximum_percent         = 200
  enable_execute_command             = var.enable_ecs_exec
  health_check_grace_period_seconds  = var.ecs_service_health_check_grace_period_seconds

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
    security_groups  = [aws_security_group.ecs_task.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = var.container_name
    container_port   = var.container_port
  }

  # ECS requires the TG to be attached to a listener before the service is
  # created. Depend on whichever listener exists (real http/https when managed,
  # or the provisional one during a pre-cutover absorb); count-0 ones are no-ops.
  depends_on = [
    aws_ecs_cluster_capacity_providers.this,
    aws_lb_listener.http,
    aws_lb_listener.https,
    aws_lb_listener.ecs_provisional,
  ]

  # Runtime deploy (GitHub Actions) and app-autoscaling own these; Terraform
  # must not revert them on the next apply.
  lifecycle {
    ignore_changes = [desired_count, task_definition]
  }

  tags = local.tags
}

resource "aws_appautoscaling_target" "ecs_service" {
  max_capacity       = var.ecs_service_max_count
  min_capacity       = var.ecs_service_min_count
  resource_id        = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.app.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "ecs_service_cpu" {
  name               = "${local.name}-ecs-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.ecs_service.resource_id
  scalable_dimension = aws_appautoscaling_target.ecs_service.scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs_service.service_namespace

  target_tracking_scaling_policy_configuration {
    target_value = var.ecs_service_cpu_target

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}
