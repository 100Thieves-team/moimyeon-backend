# Least-privilege chain:  internet -> alb -> ecs_task -> rds
# ecs_instance holds no inbound (tasks use their own ENIs in awsvpc mode).

resource "aws_security_group" "alb" {
  name        = "${local.name}-sg-alb"
  description = var.alb_sg_description
  vpc_id      = aws_vpc.this.id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.tags, {
    Name = "${local.name}-alb-sg"
  })
}

resource "aws_security_group" "ecs_instance" {
  name        = "${local.name}-ecs-instance"
  description = "ECS EC2 container instances"
  vpc_id      = aws_vpc.this.id

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.tags, {
    Name = "${local.name}-ecs-instance-sg"
  })
}

resource "aws_security_group" "ecs_task" {
  name        = "${local.name}-ecs-task"
  description = "ECS task ENIs (awsvpc)"
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "ALB to container"
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.tags, {
    Name = "${local.name}-ecs-task-sg"
  })
}

resource "aws_security_group" "rds" {
  name        = "${local.name}-sg-rds"
  description = var.rds_sg_description
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "ECS tasks to MySQL"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_task.id]
  }

  # Developer access via the SSM bastion (optional).
  dynamic "ingress" {
    for_each = var.enable_db_bastion ? [1] : []

    content {
      description     = "DB access bastion to MySQL"
      from_port       = 3306
      to_port         = 3306
      protocol        = "tcp"
      security_groups = [aws_security_group.db_bastion[0].id]
    }
  }

  # Transitional: keep existing app-host / bastion SGs allowed during cutover so
  # the currently-running container does not lose its DB connection. Remove after.
  dynamic "ingress" {
    for_each = toset(var.extra_rds_ingress_security_group_ids)

    content {
      description     = "Transitional existing SG to MySQL"
      from_port       = 3306
      to_port         = 3306
      protocol        = "tcp"
      security_groups = [ingress.value]
    }
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.tags, {
    Name = "${local.name}-rds-sg"
  })
}

resource "aws_security_group" "notification_redis" {
  count = var.enable_notification_redis ? 1 : 0

  name        = "${local.name}-notification-redis"
  description = "Notification Valkey access from ECS tasks"
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "ECS tasks to notification Valkey over TLS"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_task.id]
  }

  egress {
    description = "Valkey node communication"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    self        = true
  }

  tags = merge(local.tags, {
    Name = "${local.name}-notification-redis-sg"
  })
}
