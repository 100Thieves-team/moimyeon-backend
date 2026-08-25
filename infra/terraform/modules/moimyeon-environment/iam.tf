data "aws_iam_policy_document" "ecs_tasks_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "ec2_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

# ---------------------------------------------------------------------------
# Task execution role: pulls images, ships logs, reads SSM secrets at start.
# ---------------------------------------------------------------------------
resource "aws_iam_role" "task_execution" {
  name               = "${local.name}-ecs-task-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume_role.json

  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "task_execution_managed" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "task_execution_ssm" {
  statement {
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
    ]
    resources = local.ssm_parameter_arns
  }

  statement {
    actions   = ["kms:Decrypt"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.${data.aws_region.current.region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "task_execution_ssm" {
  name   = "${local.name}-read-ssm-secrets"
  role   = aws_iam_role.task_execution.id
  policy = data.aws_iam_policy_document.task_execution_ssm.json
}

# ---------------------------------------------------------------------------
# Task role: the application's own AWS permissions (S3 uploads + ECS Exec).
# ---------------------------------------------------------------------------
resource "aws_iam_role" "task" {
  name               = "${local.name}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume_role.json

  tags = local.tags
}

data "aws_iam_policy_document" "task" {
  statement {
    sid = "UploadObjectRW"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
    ]
    resources = ["${aws_s3_bucket.uploads.arn}/*"]
  }

  statement {
    sid       = "UploadBucketList"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.uploads.arn]
  }

  statement {
    sid     = "SummarizeResumeWithBedrock"
    actions = ["bedrock:InvokeModel"]
    resources = [
      "arn:aws:bedrock:*::foundation-model/anthropic.claude-sonnet-5",
      "arn:aws:bedrock:*:*:inference-profile/global.anthropic.claude-sonnet-5",
    ]
  }

  statement {
    sid = "EcsExecChannels"
    actions = [
      "ssmmessages:CreateControlChannel",
      "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel",
      "ssmmessages:OpenDataChannel",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "task" {
  name   = "${local.name}-app-runtime"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task.json
}

# ---------------------------------------------------------------------------
# Notification worker roles: vendor delivery is isolated from the API task.
# ---------------------------------------------------------------------------

resource "aws_iam_role" "notification_worker_execution" {
  name               = "${local.name}-notification-worker-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume_role.json

  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "notification_worker_execution_managed" {
  role       = aws_iam_role.notification_worker_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "notification_worker_execution_ssm" {
  statement {
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
    ]
    resources = local.notification_worker_ssm_parameter_arns
  }

  statement {
    actions   = ["kms:Decrypt"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.${data.aws_region.current.region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "notification_worker_execution_ssm" {
  name   = "${local.name}-notification-worker-read-secrets"
  role   = aws_iam_role.notification_worker_execution.id
  policy = data.aws_iam_policy_document.notification_worker_execution_ssm.json
}

resource "aws_iam_role" "notification_worker" {
  name               = "${local.name}-notification-worker-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume_role.json

  tags = local.tags
}

data "aws_iam_policy_document" "notification_worker" {
  dynamic "statement" {
    for_each = var.notification_email_ses_from_address == null ? [] : [var.notification_email_ses_from_address]

    content {
      sid       = "SendNotificationEmail"
      actions   = ["ses:SendEmail"]
      resources = ["*"]

      condition {
        test     = "StringEquals"
        variable = "ses:FromAddress"
        values   = [statement.value]
      }
    }
  }

  statement {
    sid = "EcsExecChannels"
    actions = [
      "ssmmessages:CreateControlChannel",
      "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel",
      "ssmmessages:OpenDataChannel",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "notification_worker" {
  name   = "${local.name}-notification-worker-runtime"
  role   = aws_iam_role.notification_worker.id
  policy = data.aws_iam_policy_document.notification_worker.json
}

# ---------------------------------------------------------------------------
# Notification Redis execution role: ships logs and reads only its password.
# ---------------------------------------------------------------------------

resource "aws_iam_role" "notification_redis_execution" {
  count = var.enable_notification_redis ? 1 : 0

  name               = "${local.name}-notification-redis-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume_role.json

  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "notification_redis_execution_managed" {
  count = var.enable_notification_redis ? 1 : 0

  role       = aws_iam_role.notification_redis_execution[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "notification_redis_execution_ssm" {
  count = var.enable_notification_redis ? 1 : 0

  statement {
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
    ]
    resources = [local.notification_redis_password_ssm_arn]
  }

  statement {
    actions   = ["kms:Decrypt"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.${data.aws_region.current.region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "notification_redis_execution_ssm" {
  count = var.enable_notification_redis ? 1 : 0

  name   = "${local.name}-notification-redis-read-password"
  role   = aws_iam_role.notification_redis_execution[0].id
  policy = data.aws_iam_policy_document.notification_redis_execution_ssm[0].json
}

# ---------------------------------------------------------------------------
# ECS container instance role (EC2 capacity).
# ---------------------------------------------------------------------------
resource "aws_iam_role" "ecs_instance" {
  name               = "${local.name}-ecs-instance"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json

  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "ecs_instance_ecs" {
  role       = aws_iam_role.ecs_instance.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceforEC2Role"
}

resource "aws_iam_role_policy_attachment" "ecs_instance_ssm" {
  role       = aws_iam_role.ecs_instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ecs_instance" {
  name = "${local.name}-ecs-instance"
  role = aws_iam_role.ecs_instance.name

  tags = local.tags
}

data "aws_iam_policy_document" "ecs_load_balancer_infrastructure_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ecs_load_balancer_infrastructure" {
  count = local.ecs_blue_green_enabled ? 1 : 0

  name               = "${local.name}-ecs-load-balancer-infrastructure"
  assume_role_policy = data.aws_iam_policy_document.ecs_load_balancer_infrastructure_assume_role.json

  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "ecs_load_balancer_infrastructure" {
  count = local.ecs_blue_green_enabled ? 1 : 0

  role       = aws_iam_role.ecs_load_balancer_infrastructure[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonECSInfrastructureRolePolicyForLoadBalancers"
}

# ---------------------------------------------------------------------------
# GitHub Actions deploy role (OIDC), restricted to this env's branch.
# ---------------------------------------------------------------------------
locals {
  github_oidc_host = "token.actions.githubusercontent.com"

  # GitHub is migrating OIDC subjects to an immutable form that embeds numeric
  # org/repo IDs (repo:org@id/repo@id:ref:...). Trust both the legacy name-based
  # sub and the immutable one so the deploy works across the transition.
  github_deploy_subs = concat(
    ["repo:${var.github_repository}:ref:refs/heads/${var.github_branch}"],
    var.github_deploy_immutable_repo != null ? ["repo:${var.github_deploy_immutable_repo}:ref:refs/heads/${var.github_branch}"] : [],
    [for environment in var.github_deploy_environments : "repo:${var.github_repository}:environment:${environment}"],
    var.github_deploy_immutable_repo != null ? [
      for environment in var.github_deploy_environments : "repo:${var.github_deploy_immutable_repo}:environment:${environment}"
    ] : [],
  )
}

data "aws_iam_policy_document" "github_deploy_assume_role" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [var.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.github_oidc_host}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "${local.github_oidc_host}:sub"
      values   = local.github_deploy_subs
    }
  }

}

resource "aws_iam_role" "github_deploy" {
  name               = "${local.name}-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_deploy_assume_role.json

  tags = local.tags
}

data "aws_iam_policy_document" "github_deploy" {
  statement {
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:DescribeRepositories",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [
      aws_ecr_repository.app.arn,
      aws_ecr_repository.notification_worker.arn,
    ]
  }

  dynamic "statement" {
    for_each = length(var.github_deploy_additional_ecr_read_repository_arns) > 0 ? [1] : []

    content {
      actions = [
        "ecr:BatchCheckLayerAvailability",
        "ecr:BatchGetImage",
        "ecr:DescribeImages",
        "ecr:DescribeRepositories",
        "ecr:GetDownloadUrlForLayer",
      ]
      resources = var.github_deploy_additional_ecr_read_repository_arns
    }
  }

  dynamic "statement" {
    for_each = length(var.github_deploy_additional_ssm_read_parameter_arns) > 0 ? [1] : []

    content {
      actions   = ["ssm:GetParameter"]
      resources = var.github_deploy_additional_ssm_read_parameter_arns
    }
  }

  statement {
    actions = [
      "ecs:DescribeServices",
      "ecs:DescribeTaskDefinition",
      "ecs:DescribeTasks",
      "ecs:ListTasks",
      "ecs:RegisterTaskDefinition",
      "ecs:UpdateService",
    ]
    resources = ["*"]
  }

  statement {
    actions   = ["elasticloadbalancing:DescribeTargetHealth"]
    resources = ["*"]
  }

  statement {
    actions = ["ssm:PutParameter"]
    resources = [
      aws_ssm_parameter.image_uri.arn,
      aws_ssm_parameter.notification_worker_image_uri.arn,
    ]
  }

  statement {
    actions = [
      "ssm:GetParameter",
      "ssm:PutParameter",
    ]
    resources = [
      "arn:aws:ssm:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:parameter${local.deployment_bundle_parameter_prefix}/*",
    ]
  }

  statement {
    actions = ["iam:PassRole"]
    resources = concat(
      [
        aws_iam_role.task.arn,
        aws_iam_role.task_execution.arn,
        aws_iam_role.notification_worker.arn,
        aws_iam_role.notification_worker_execution.arn,
      ],
      aws_iam_role.ecs_load_balancer_infrastructure[*].arn,
    )
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "${local.name}-github-deploy"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}
