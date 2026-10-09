# ---------------------------------------------------------------------------
# PR-built deploy candidates (MOI-565).
#
# Internal PR CI builds the API/Worker images once and pushes them here, tagged
# by the merge ref's Git tree. The dev ruleset requires an up-to-date branch, so
# the merged commit has the same tree and the deploy copies that exact digest
# into the deploy repositories instead of rebuilding. PR CI never writes to the
# deploy repositories, ECS, or SSM.
#
# The deploy config is published to SSM by the same apply that changes it, so a
# deploy reads it directly instead of waiting for a per-commit Terraform run.
# ---------------------------------------------------------------------------
locals {
  pr_image_candidate_repositories = var.enable_pr_image_candidates ? {
    core-api    = "${aws_ecr_repository.app.name}-candidate"
    core-worker = "${aws_ecr_repository.notification_worker.name}-candidate"
  } : {}

  deploy_config_parameter_name = "/${var.project}/${var.environment}/deploy/config"
  publish_deploy_config        = var.enable_pr_image_candidates || var.publish_deploy_config
  # Written by the Terraform Apply workflow (not Terraform) after each
  # successful dev apply: the source SHA whose infra/terraform tree is live.
  terraform_applied_sha_parameter_name = "/${var.project}/${var.environment}/deploy/terraform-applied-sha"

  github_pr_image_subs = concat(
    ["repo:${var.github_repository}:pull_request"],
    var.github_deploy_immutable_repo != null ? ["repo:${var.github_deploy_immutable_repo}:pull_request"] : [],
  )
}

resource "aws_ecr_repository" "pr_image_candidate" {
  for_each = local.pr_image_candidate_repositories

  name                 = each.value
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = false
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = merge(local.tags, {
    Name = each.value
  })
}

# Candidates are disposable: the deploy copies the digest into the deploy
# repository, where deployment markers keep it for promotion and rollback.
resource "aws_ecr_lifecycle_policy" "pr_image_candidate" {
  for_each = aws_ecr_repository.pr_image_candidate

  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire PR candidate images"
        selection = {
          tagStatus   = "any"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 14
        }
        action = {
          type = "expire"
        }
      },
    ]
  })
}

data "aws_iam_policy_document" "github_pr_image_assume_role" {
  count = var.enable_pr_image_candidates ? 1 : 0

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

    # Fork PRs never receive an OIDC token; only same-repository PRs reach here.
    condition {
      test     = "StringEquals"
      variable = "${local.github_oidc_host}:sub"
      values   = local.github_pr_image_subs
    }

    condition {
      test     = "StringEquals"
      variable = "${local.github_oidc_host}:repository_id"
      values   = [var.github_repository_id]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.github_oidc_host}:repository_owner_id"
      values   = [var.github_repository_owner_id]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.github_oidc_host}:workflow"
      values   = ["CI"]
    }

    # STS has no base_ref condition key; CI limits pushes to PRs into dev. A
    # candidate only deploys when a dev merge commit has the same tree.
    condition {
      test     = "StringLike"
      variable = "${local.github_oidc_host}:ref"
      values   = ["refs/pull/*/merge"]
    }
  }
}

resource "aws_iam_role" "github_pr_image" {
  count = var.enable_pr_image_candidates ? 1 : 0

  name               = "${local.name}-github-pr-image"
  assume_role_policy = data.aws_iam_policy_document.github_pr_image_assume_role[0].json

  tags = local.tags
}

data "aws_iam_policy_document" "github_pr_image" {
  count = var.enable_pr_image_candidates ? 1 : 0

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
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [for repository in aws_ecr_repository.pr_image_candidate : repository.arn]
  }
}

resource "aws_iam_role_policy" "github_pr_image" {
  count = var.enable_pr_image_candidates ? 1 : 0

  name   = "${local.name}-github-pr-image"
  role   = aws_iam_role.github_pr_image[0].id
  policy = data.aws_iam_policy_document.github_pr_image[0].json
}

# Same allowlist as the former per-run deploy-config artifact. Every value is a
# non-secret identifier or URL; secrets stay in pre-created SecureStrings.
# Live publishes it too (without candidates) so promotion reads task templates
# after the Terraform boundary instead of the run-start GitHub variable snapshot.
resource "aws_ssm_parameter" "deploy_config" {
  count = local.publish_deploy_config ? 1 : 0

  name        = local.deploy_config_parameter_name
  description = "Non-secret ${local.name} deploy wiring for the GitHub deploy workflow"
  type        = "String"
  value = jsonencode({
    schema_version = 2
    environment    = var.environment
    config = {
      aws_region                 = data.aws_region.current.region
      role_arn                   = aws_iam_role.github_deploy.arn
      ecr_repository_url         = aws_ecr_repository.app.repository_url
      ecs_cluster                = aws_ecs_cluster.this.name
      ecs_service                = aws_ecs_service.app.name
      ecs_container              = var.container_name
      ecs_task_definition        = aws_ecs_task_definition.app.arn
      image_uri_parameter        = aws_ssm_parameter.image_uri.name
      app_url                    = local.app_url
      bundle_parameter_prefix    = local.deployment_bundle_parameter_prefix
      worker_ecr_repository_url  = aws_ecr_repository.notification_worker.repository_url
      worker_ecs_service         = aws_ecs_service.notification_worker.name
      worker_ecs_container       = var.notification_worker_container_name
      worker_ecs_task_definition = aws_ecs_task_definition.notification_worker.arn
      worker_image_uri_parameter = aws_ssm_parameter.notification_worker_image_uri.name
    }
    candidate_repository_urls = {
      for family, repository in aws_ecr_repository.pr_image_candidate : family => repository.repository_url
    }
  })

  tags = local.tags
}
