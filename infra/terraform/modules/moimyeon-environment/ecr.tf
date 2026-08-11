resource "aws_ecr_repository" "app" {
  name                 = coalesce(var.ecr_repository_name, "${local.name}-core-api")
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = merge(local.tags, {
    Name = coalesce(var.ecr_repository_name, "${local.name}-core-api")
  })
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep the most recent images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.ecr_image_retention_count
        }
        action = {
          type = "expire"
        }
      },
    ]
  })
}

resource "aws_ecr_repository" "notification_worker" {
  name                 = coalesce(var.notification_worker_ecr_repository_name, "${local.name}-core-worker")
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = merge(local.tags, {
    Name = coalesce(var.notification_worker_ecr_repository_name, "${local.name}-core-worker")
  })
}

resource "aws_ecr_lifecycle_policy" "notification_worker" {
  repository = aws_ecr_repository.notification_worker.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep the most recent worker images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.ecr_image_retention_count
        }
        action = {
          type = "expire"
        }
      },
    ]
  })
}
