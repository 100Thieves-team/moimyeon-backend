data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

locals {
  terraform_state_bucket_name = coalesce(
    var.terraform_state_bucket_name,
    "${var.project}-terraform-state-${data.aws_caller_identity.current.account_id}-${data.aws_region.current.region}",
  )
  terraform_plan_artifact_bucket_name = coalesce(
    var.terraform_plan_artifact_bucket_name,
    "${var.project}-terraform-plans-${data.aws_caller_identity.current.account_id}-${data.aws_region.current.region}",
  )

  terraform_plan_roles = {
    terraform-review-plan = {
      name              = "${var.project}-terraform-review-plan"
      artifact_prefix   = "plans/pr"
      workflow          = "Terraform Plan"
      ref               = null
      job_workflow_refs = []
    }
    terraform-drift-plan = {
      name              = "${var.project}-terraform-drift-plan"
      artifact_prefix   = "plans/drift"
      workflow          = "Terraform Plan"
      ref               = "refs/heads/dev"
      job_workflow_refs = []
    }
    terraform-apply-plan = {
      name            = "${var.project}-terraform-apply-plan"
      artifact_prefix = "plans/apply"
      workflow        = "Terraform Apply"
      ref             = "refs/heads/dev"
      job_workflow_refs = [
        "${var.github_repository}/.github/workflows/terraform-plan-environment.yml@refs/heads/dev",
      ]
    }
  }

  terraform_apply_environments = toset(["shared", "dev", "live"])
  terraform_oidc_trusts = merge(
    local.terraform_plan_roles,
    {
      for environment in local.terraform_apply_environments :
      "${environment}-infra" => {
        workflow = "Terraform Apply"
        ref      = "refs/heads/dev"
        job_workflow_refs = [
          "${var.github_repository}/.github/workflows/terraform-apply-environment.yml@refs/heads/dev",
          "${var.github_repository}/.github/workflows/terraform-sync-variables.yml@refs/heads/dev",
        ]
      }
    },
  )
  terraform_state_keys = {
    for environment in local.terraform_apply_environments :
    environment => "${environment}/terraform.tfstate"
  }
}

# Environments are unprotected variable namespaces, not an authorization gate.
# Repository/ref/workflow claims prevent a PR from reusing a privileged
# Environment subject, while job_workflow_ref pins reusable apply boundaries.
data "aws_iam_policy_document" "terraform_github_assume_role" {
  for_each = local.terraform_oidc_trusts

  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values = concat(
        ["repo:${var.github_repository}:environment:${each.key}"],
        var.github_immutable_repository == null ? [] : [
          "repo:${var.github_immutable_repository}:environment:${each.key}",
        ],
      )
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:repository_id"
      values   = [var.github_repository_id]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:repository_owner_id"
      values   = [var.github_repository_owner_id]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:workflow"
      values   = [each.value.workflow]
    }

    dynamic "condition" {
      for_each = each.value.ref == null ? [] : [each.value.ref]
      iterator = ref_claim

      content {
        test     = "StringEquals"
        variable = "token.actions.githubusercontent.com:ref"
        values   = [ref_claim.value]
      }
    }

    dynamic "condition" {
      for_each = length(each.value.job_workflow_refs) == 0 ? [] : [each.value.job_workflow_refs]
      iterator = workflow_claim

      content {
        test     = "StringEquals"
        variable = "token.actions.githubusercontent.com:job_workflow_ref"
        values   = workflow_claim.value
      }
    }
  }
}

# ---------------------------------------------------------------------------
# Short-lived exact-plan artifacts: private, KMS encrypted, immutable to CI
# readers, and automatically expired after 24 hours.
# ---------------------------------------------------------------------------

data "aws_iam_policy_document" "terraform_plan_kms" {
  statement {
    sid       = "EnableAccountIAMPolicies"
    effect    = "Allow"
    actions   = ["kms:*"]
    resources = ["*"]

    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"]
    }
  }
}

resource "aws_kms_key" "terraform_plan_artifacts" {
  description             = "KMS key for short-lived ${var.project} Terraform exact-plan artifacts"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.terraform_plan_kms.json

  lifecycle {
    prevent_destroy = true
  }

  tags = merge(local.tags, {
    Name = "${var.project}-terraform-plan-artifacts"
  })
}

resource "aws_kms_alias" "terraform_plan_artifacts" {
  name          = "alias/${var.project}-terraform-plan-artifacts"
  target_key_id = aws_kms_key.terraform_plan_artifacts.key_id
}

resource "aws_s3_bucket" "terraform_plan_artifacts" {
  bucket        = local.terraform_plan_artifact_bucket_name
  force_destroy = false

  lifecycle {
    prevent_destroy = true
  }

  tags = merge(local.tags, {
    Name = local.terraform_plan_artifact_bucket_name
  })
}

resource "aws_s3_bucket_ownership_controls" "terraform_plan_artifacts" {
  bucket = aws_s3_bucket.terraform_plan_artifacts.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "terraform_plan_artifacts" {
  bucket = aws_s3_bucket.terraform_plan_artifacts.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "terraform_plan_artifacts" {
  bucket = aws_s3_bucket.terraform_plan_artifacts.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "terraform_plan_artifacts" {
  bucket = aws_s3_bucket.terraform_plan_artifacts.id

  rule {
    bucket_key_enabled = true

    apply_server_side_encryption_by_default {
      kms_master_key_id = aws_kms_key.terraform_plan_artifacts.arn
      sse_algorithm     = "aws:kms"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "terraform_plan_artifacts" {
  bucket = aws_s3_bucket.terraform_plan_artifacts.id

  depends_on = [aws_s3_bucket_versioning.terraform_plan_artifacts]

  rule {
    id     = "expire-exact-plans-after-24-hours"
    status = "Enabled"

    filter {}

    expiration {
      days = 1
    }

    noncurrent_version_expiration {
      noncurrent_days = 1
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }

  rule {
    id     = "remove-expired-plan-delete-markers"
    status = "Enabled"

    filter {}

    expiration {
      expired_object_delete_marker = true
    }
  }
}

# ---------------------------------------------------------------------------
# Terraform plan writers use a repository-owned metadata refresh allowlist.
# They can only create objects in their own prefix and cannot read data-plane
# objects, application secret values, or plan artifacts.
# ---------------------------------------------------------------------------

resource "aws_iam_role" "terraform_plan" {
  for_each = local.terraform_plan_roles

  name                 = each.value.name
  assume_role_policy   = data.aws_iam_policy_document.terraform_github_assume_role[each.key].json
  max_session_duration = 3600

  tags = local.tags
}

data "aws_iam_policy_document" "terraform_plan_refresh" {
  statement {
    sid = "RefreshTerraformResourceMetadata"
    actions = [
      "acm:Describe*",
      "acm:List*",
      "application-autoscaling:Describe*",
      "application-autoscaling:ListTagsForResource",
      "autoscaling:Describe*",
      "cloudwatch:Describe*",
      "cloudwatch:List*",
      "ec2:Describe*",
      "ec2:GetLaunchTemplateData",
      "ecr:Describe*",
      "ecr:GetLifecyclePolicy",
      "ecr:GetRepositoryPolicy",
      "ecr:ListTagsForResource",
      "ecs:Describe*",
      "ecs:ListAccountSettings",
      "ecs:ListAttributes",
      "ecs:ListServices",
      "ecs:ListTagsForResource",
      "elasticfilesystem:Describe*",
      "elasticfilesystem:ListTagsForResource",
      "elasticloadbalancing:Describe*",
      "iam:Get*",
      "iam:List*",
      "kms:DescribeKey",
      "kms:GetKeyPolicy",
      "kms:GetKeyRotationStatus",
      "kms:ListAliases",
      "kms:ListResourceTags",
      "logs:Describe*",
      "logs:ListTagsForResource",
      "rds:Describe*",
      "rds:ListTagsForResource",
      "route53:Get*",
      "route53:List*",
      "route53domains:GetDomainDetail",
      "route53domains:GetOperationDetail",
      "route53domains:ListTagsForDomain",
      "servicediscovery:Get*",
      "servicediscovery:List*",
      "ssm:DescribeParameters",
      "ssm:ListTagsForResource",
      "sts:GetCallerIdentity",
      "tag:GetResources",
      "tag:GetTagKeys",
      "tag:GetTagValues",
      "wafv2:Get*",
      "wafv2:List*",
    ]
    resources = ["*"]
  }

  statement {
    sid = "RefreshTerraformBucketMetadata"
    actions = [
      "s3:GetAccelerateConfiguration",
      "s3:GetBucket*",
      "s3:GetEncryptionConfiguration",
      "s3:GetLifecycleConfiguration",
      "s3:GetReplicationConfiguration",
      "s3:ListBucket",
    ]
    resources = ["arn:aws:s3:::${var.project}-*"]
  }

  statement {
    sid     = "ReadNonSecretTerraformParameters"
    actions = ["ssm:GetParameter"]
    resources = [
      "arn:aws:ssm:*::parameter/aws/service/ecs/optimized-ami/*",
      "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/${var.project}/*/core-api/IMAGE_URI",
      "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/${var.project}/*/core-worker/IMAGE_URI",
    ]
  }
}

resource "aws_iam_policy" "terraform_plan_refresh" {
  name        = "${var.project}-terraform-plan-refresh"
  description = "Metadata-only refresh permissions for ${var.project} Terraform plan roles"
  policy      = data.aws_iam_policy_document.terraform_plan_refresh.json

  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "terraform_plan_refresh" {
  for_each = local.terraform_plan_roles

  role       = aws_iam_role.terraform_plan[each.key].name
  policy_arn = aws_iam_policy.terraform_plan_refresh.arn
}

data "aws_iam_policy_document" "terraform_plan" {
  for_each = local.terraform_plan_roles

  statement {
    sid = "ReadTerraformStateBucket"
    actions = [
      "s3:GetBucketLocation",
      "s3:ListBucket",
    ]
    resources = ["arn:aws:s3:::${local.terraform_state_bucket_name}"]
  }

  statement {
    sid       = "ReadTerraformStateObjects"
    actions   = ["s3:GetObject"]
    resources = [for key in values(local.terraform_state_keys) : "arn:aws:s3:::${local.terraform_state_bucket_name}/${key}"]
  }

  statement {
    sid = "UseTerraformStateLocks"
    actions = [
      "dynamodb:DeleteItem",
      "dynamodb:GetItem",
      "dynamodb:PutItem",
    ]
    resources = [
      "arn:aws:dynamodb:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:table/${var.terraform_lock_table_name}",
    ]

    condition {
      test     = "ForAllValues:StringLike"
      variable = "dynamodb:LeadingKeys"
      values = flatten([
        for key in values(local.terraform_state_keys) : [
          "${local.terraform_state_bucket_name}/${key}",
          "${local.terraform_state_bucket_name}/${key}-md5",
        ]
      ])
    }
  }

  statement {
    sid     = "DescribeTerraformStateLockTable"
    actions = ["dynamodb:DescribeTable"]
    resources = [
      "arn:aws:dynamodb:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:table/${var.terraform_lock_table_name}",
    ]
  }

  statement {
    sid       = "CreateOwnExactPlanArtifacts"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.terraform_plan_artifacts.arn}/${each.value.artifact_prefix}/*"]
  }

  statement {
    sid = "EncryptOwnExactPlanArtifacts"
    actions = [
      "kms:DescribeKey",
      "kms:Encrypt",
      "kms:GenerateDataKey",
    ]
    resources = [aws_kms_key.terraform_plan_artifacts.arn]
  }

  statement {
    sid    = "DenyReadingOrDeletingExactPlanArtifacts"
    effect = "Deny"
    actions = [
      "s3:DeleteObject",
      "s3:DeleteObjectVersion",
      "s3:GetObject",
      "s3:GetObjectVersion",
    ]
    resources = ["${aws_s3_bucket.terraform_plan_artifacts.arn}/*"]
  }

  statement {
    sid       = "DenyDecryptingExactPlanArtifacts"
    effect    = "Deny"
    actions   = ["kms:Decrypt"]
    resources = [aws_kms_key.terraform_plan_artifacts.arn]
  }

  statement {
    sid    = "DenyApplicationSecretValueReads"
    effect = "Deny"
    actions = [
      "secretsmanager:BatchGetSecretValue",
      "secretsmanager:GetSecretValue",
      "ssm:GetParameterHistory",
      "ssm:GetParametersByPath",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "DenyApplicationSecureStringReads"
    effect = "Deny"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
    ]
    resources = [
      "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/${var.project}/*/core-api/DB_PASSWORD",
      "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/${var.project}/*/core-api/GOOGLE_OAUTH_CLIENT_SECRET",
      "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/${var.project}/*/core-api/JWT_SECRET",
      "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/${var.project}/*/core-worker/FIREBASE_SERVICE_ACCOUNT_JSON",
      "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/${var.project}/*/core-worker/NOTIFICATION_EMAIL_GMAIL_APP_PASSWORD",
      "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/${var.project}/*/notification-redis/PASSWORD",
      "arn:aws:ssm:*:${data.aws_caller_identity.current.account_id}:parameter/${var.project}/*/shared/STORAGE_REDIS_URL",
    ]
  }

  statement {
    sid       = "DenyGeneralKMSDecryption"
    effect    = "Deny"
    actions   = ["kms:Decrypt"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "terraform_plan" {
  for_each = local.terraform_plan_roles

  name   = "${var.project}-terraform-${replace(each.key, "terraform-", "")}-boundary"
  role   = aws_iam_role.terraform_plan[each.key].id
  policy = data.aws_iam_policy_document.terraform_plan[each.key].json
}

# ---------------------------------------------------------------------------
# Exact-plan apply roles. Environment approval is the human boundary. Each role
# can mutate only its own state key and project IAM namespace; artifact writes
# and cross-environment state access are explicitly denied.
# ---------------------------------------------------------------------------

resource "aws_iam_role" "terraform_apply" {
  for_each = local.terraform_apply_environments

  name                 = "${var.project}-terraform-apply-${each.key}"
  assume_role_policy   = data.aws_iam_policy_document.terraform_github_assume_role["${each.key}-infra"].json
  max_session_duration = 3600

  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "terraform_apply_power_user" {
  for_each = local.terraform_apply_environments

  role       = aws_iam_role.terraform_apply[each.key].name
  policy_arn = "arn:aws:iam::aws:policy/PowerUserAccess"
}

data "aws_iam_policy_document" "terraform_apply" {
  for_each = local.terraform_apply_environments

  statement {
    sid = "UseOwnTerraformState"
    actions = [
      "s3:GetBucketLocation",
      "s3:ListBucket",
    ]
    resources = ["arn:aws:s3:::${local.terraform_state_bucket_name}"]
  }

  statement {
    sid = "MutateOwnTerraformStateObject"
    actions = [
      "s3:DeleteObject",
      "s3:GetObject",
      "s3:PutObject",
    ]
    resources = ["arn:aws:s3:::${local.terraform_state_bucket_name}/${local.terraform_state_keys[each.key]}"]
  }

  statement {
    sid    = "DenyCrossEnvironmentStateObjects"
    effect = "Deny"
    actions = [
      "s3:DeleteObject",
      "s3:DeleteObjectTagging",
      "s3:DeleteObjectVersion",
      "s3:GetObject",
      "s3:GetObjectVersion",
      "s3:PutObjectAcl",
      "s3:PutObject",
      "s3:PutObjectTagging",
      "s3:RestoreObject",
    ]
    resources = [
      for environment, key in local.terraform_state_keys :
      "arn:aws:s3:::${local.terraform_state_bucket_name}/${key}" if environment != each.key
    ]
  }

  statement {
    sid    = "ProtectTerraformStateBucketConfiguration"
    effect = "Deny"
    not_actions = [
      "s3:GetBucketLocation",
      "s3:ListBucket",
    ]
    resources = ["arn:aws:s3:::${local.terraform_state_bucket_name}"]
  }

  statement {
    sid = "UseTerraformStateLocks"
    actions = [
      "dynamodb:DeleteItem",
      "dynamodb:GetItem",
      "dynamodb:PutItem",
    ]
    resources = [
      "arn:aws:dynamodb:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:table/${var.terraform_lock_table_name}",
    ]


    condition {
      test     = "ForAllValues:StringLike"
      variable = "dynamodb:LeadingKeys"
      values = [
        "${local.terraform_state_bucket_name}/${local.terraform_state_keys[each.key]}",
        "${local.terraform_state_bucket_name}/${local.terraform_state_keys[each.key]}-md5",
      ]
    }
  }

  statement {
    sid     = "DescribeTerraformStateLockTable"
    actions = ["dynamodb:DescribeTable"]
    resources = [
      "arn:aws:dynamodb:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:table/${var.terraform_lock_table_name}",
    ]
  }

  statement {
    sid    = "ProtectTerraformStateLockTable"
    effect = "Deny"
    actions = [
      "dynamodb:DeleteTable",
      "dynamodb:UpdateTable",
      "dynamodb:UpdateTimeToLive",
    ]
    resources = [
      "arn:aws:dynamodb:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:table/${var.terraform_lock_table_name}",
    ]
  }

  statement {
    sid = "ReadApprovedExactPlanArtifacts"
    actions = [
      "s3:GetObject",
      "s3:GetObjectVersion",
    ]
    resources = ["${aws_s3_bucket.terraform_plan_artifacts.arn}/plans/apply/*"]
  }

  statement {
    sid    = "DenyExactPlanArtifactMutation"
    effect = "Deny"
    actions = [
      "s3:DeleteObject",
      "s3:DeleteObjectVersion",
      "s3:PutObject",
      "s3:PutObjectAcl",
    ]
    resources = ["${aws_s3_bucket.terraform_plan_artifacts.arn}/*"]
  }

  dynamic "statement" {
    for_each = each.key == "shared" ? [] : [1]

    content {
      sid         = "ProtectExactPlanBucketConfiguration"
      effect      = "Deny"
      not_actions = ["s3:GetBucketLocation"]
      resources   = [aws_s3_bucket.terraform_plan_artifacts.arn]
    }
  }

  statement {
    sid    = "DenyUnapprovedPlanArtifactRead"
    effect = "Deny"
    actions = [
      "s3:GetObject",
      "s3:GetObjectVersion",
    ]
    resources = [
      "${aws_s3_bucket.terraform_plan_artifacts.arn}/plans/pr/*",
      "${aws_s3_bucket.terraform_plan_artifacts.arn}/plans/drift/*",
    ]
  }

  statement {
    sid = "DecryptApprovedExactPlanArtifacts"
    actions = [
      "kms:Decrypt",
      "kms:DescribeKey",
    ]
    resources = [aws_kms_key.terraform_plan_artifacts.arn]
  }

  dynamic "statement" {
    for_each = each.key == "shared" ? [] : [1]

    content {
      sid         = "ProtectExactPlanKMSKey"
      effect      = "Deny"
      not_actions = ["kms:Decrypt", "kms:DescribeKey"]
      resources   = [aws_kms_key.terraform_plan_artifacts.arn]
    }
  }

  statement {
    sid = "ManageProjectIAMRoles"
    actions = [
      "iam:AddRoleToInstanceProfile",
      "iam:AttachRolePolicy",
      "iam:CreateInstanceProfile",
      "iam:CreateRole",
      "iam:DeleteInstanceProfile",
      "iam:DeleteRole",
      "iam:DeleteRolePolicy",
      "iam:DetachRolePolicy",
      "iam:GetInstanceProfile",
      "iam:GetRole",
      "iam:GetRolePolicy",
      "iam:ListAttachedRolePolicies",
      "iam:ListInstanceProfilesForRole",
      "iam:ListInstanceProfileTags",
      "iam:ListRolePolicies",
      "iam:ListRoleTags",
      "iam:PutRolePolicy",
      "iam:RemoveRoleFromInstanceProfile",
      "iam:TagInstanceProfile",
      "iam:TagRole",
      "iam:UntagInstanceProfile",
      "iam:UntagRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:UpdateRole",
      "iam:UpdateRoleDescription",
    ]
    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:instance-profile/${var.project}-${each.key == "shared" ? "*" : "${each.key}-*"}",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${var.project}-${each.key == "shared" ? "*" : "${each.key}-*"}",
    ]
  }

  statement {
    sid     = "PassProjectRolesToAWSService"
    actions = ["iam:PassRole"]
    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${var.project}-${each.key == "shared" ? "*" : "${each.key}-*"}",
    ]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values = [
        "autoscaling.amazonaws.com",
        "ec2.amazonaws.com",
        "ecs-tasks.amazonaws.com",
        "ecs.amazonaws.com",
      ]
    }
  }

  dynamic "statement" {
    for_each = each.key == "shared" ? [1] : []

    content {
      sid = "ManageProjectIAMPolicies"
      actions = [
        "iam:CreatePolicy",
        "iam:CreatePolicyVersion",
        "iam:DeletePolicy",
        "iam:DeletePolicyVersion",
        "iam:GetPolicy",
        "iam:GetPolicyVersion",
        "iam:ListEntitiesForPolicy",
        "iam:ListPolicyTags",
        "iam:ListPolicyVersions",
        "iam:SetDefaultPolicyVersion",
        "iam:TagPolicy",
        "iam:UntagPolicy",
      ]
      resources = [
        "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/${var.project}-*",
      ]
    }
  }

  dynamic "statement" {
    for_each = each.key == "shared" ? [1] : []

    content {
      sid = "ManageGitHubOIDCProvider"
      actions = [
        "iam:AddClientIDToOpenIDConnectProvider",
        "iam:CreateOpenIDConnectProvider",
        "iam:DeleteOpenIDConnectProvider",
        "iam:GetOpenIDConnectProvider",
        "iam:ListOpenIDConnectProviderTags",
        "iam:RemoveClientIDFromOpenIDConnectProvider",
        "iam:TagOpenIDConnectProvider",
        "iam:UntagOpenIDConnectProvider",
        "iam:UpdateOpenIDConnectProviderThumbprint",
      ]
      resources = [local.github_oidc_provider_arn]
    }
  }

  statement {
    sid       = "CreateRequiredServiceLinkedRoles"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/aws-service-role/*"]

    condition {
      test     = "StringLike"
      variable = "iam:AWSServiceName"
      values = [
        "autoscaling.amazonaws.com",
        "ecs.amazonaws.com",
        "elasticloadbalancing.amazonaws.com",
        "rds.amazonaws.com",
      ]
    }
  }
}

resource "aws_iam_role_policy" "terraform_apply" {
  for_each = local.terraform_apply_environments

  name   = "${var.project}-terraform-apply-${each.key}-boundary"
  role   = aws_iam_role.terraform_apply[each.key].id
  policy = data.aws_iam_policy_document.terraform_apply[each.key].json
}

# Bucket policy is the final invariant even if a managed policy later expands.
data "aws_iam_policy_document" "terraform_plan_artifacts" {
  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.terraform_plan_artifacts.arn,
      "${aws_s3_bucket.terraform_plan_artifacts.arn}/*",
    ]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }

  statement {
    sid       = "DenyArtifactDeletion"
    effect    = "Deny"
    actions   = ["s3:DeleteObject", "s3:DeleteObjectVersion"]
    resources = ["${aws_s3_bucket.terraform_plan_artifacts.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }
  }

  statement {
    sid       = "DenyUploadsWithoutKMS"
    effect    = "Deny"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.terraform_plan_artifacts.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "StringNotEquals"
      variable = "s3:x-amz-server-side-encryption"
      values   = ["aws:kms"]
    }
  }

  statement {
    sid       = "DenyUploadsWithWrongKMSKey"
    effect    = "Deny"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.terraform_plan_artifacts.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "StringNotEquals"
      variable = "s3:x-amz-server-side-encryption-aws-kms-key-id"
      values   = [aws_kms_key.terraform_plan_artifacts.arn]
    }
  }

  statement {
    sid       = "DenyNonConditionalArtifactWrites"
    effect    = "Deny"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.terraform_plan_artifacts.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Null"
      variable = "s3:if-none-match"
      values   = ["true"]
    }

    condition {
      test     = "Bool"
      variable = "s3:ObjectCreationOperation"
      values   = ["true"]
    }
  }

  dynamic "statement" {
    for_each = local.terraform_plan_roles
    iterator = plan_role

    content {
      sid     = "Deny${replace(title(plan_role.key), "-", "")}CrossPrefixWrites"
      effect  = "Deny"
      actions = ["s3:PutObject"]
      resources = [
        for role_key, role_config in local.terraform_plan_roles :
        "${aws_s3_bucket.terraform_plan_artifacts.arn}/${role_config.artifact_prefix}/*" if role_key != plan_role.key
      ]

      principals {
        type        = "*"
        identifiers = ["*"]
      }

      condition {
        test     = "ArnEquals"
        variable = "aws:PrincipalArn"
        values   = [aws_iam_role.terraform_plan[plan_role.key].arn]
      }
    }
  }

  statement {
    sid    = "DenyPlanWriterArtifactRead"
    effect = "Deny"
    actions = [
      "s3:GetObject",
      "s3:GetObjectVersion",
    ]
    resources = ["${aws_s3_bucket.terraform_plan_artifacts.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "ArnLike"
      variable = "aws:PrincipalArn"
      values   = values(aws_iam_role.terraform_plan)[*].arn
    }
  }

  statement {
    sid    = "DenyApplyRoleArtifactMutation"
    effect = "Deny"
    actions = [
      "s3:DeleteObject",
      "s3:DeleteObjectVersion",
      "s3:PutObject",
    ]
    resources = ["${aws_s3_bucket.terraform_plan_artifacts.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "ArnLike"
      variable = "aws:PrincipalArn"
      values   = values(aws_iam_role.terraform_apply)[*].arn
    }
  }
}

resource "aws_s3_bucket_policy" "terraform_plan_artifacts" {
  bucket = aws_s3_bucket.terraform_plan_artifacts.id
  policy = data.aws_iam_policy_document.terraform_plan_artifacts.json
}
