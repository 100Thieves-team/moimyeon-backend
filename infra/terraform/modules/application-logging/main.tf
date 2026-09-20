terraform {
  required_version = ">= 1.7.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 6.57" }
  }
}

locals {
  provisioned = var.mode != "disabled"
  services    = local.provisioned ? var.services : {}
  buckets     = local.provisioned ? toset(["logs", "config"]) : toset([])
  # Add a revision instead of editing a deployed revision. Old ECS revisions still read it.
  revisions       = ["v1"]
  active_revision = "v1"
  router_image    = "public.ecr.aws/aws-observability/aws-for-fluent-bit:3.4.17@sha256:940eee58ec25fc5b92328da54e9c834c0bb4656c76320ceb24be7a8dd4063029"
  router_cpu      = 64
  router_memory   = 128
  driver_memory   = 32
  groups = merge([
    for key, service in local.services : {
      for kind, days in { ops = 7, debug = 3, router = 7, fallback = 3 } : "${key}.${kind}" => {
        name                  = "/ecs/${var.name}/${service.container_name}/${kind}"
        days                  = days
      }
    }
  ]...)
  configurations = merge([
    for revision in local.revisions : {
      for key, service in local.services : "${key}.${revision}" => {
        service  = key
        revision = revision
        content = templatefile("${path.module}/router/${revision}/fluent-bit.conf.tftpl", {
          container_name                    = service.container_name
          region                            = var.region
          bucket                            = aws_s3_bucket.this["logs"].bucket
          environment                       = var.environment
          service_name                      = service.service_name
          ops_group                         = aws_cloudwatch_log_group.this["${key}.ops"].name
          debug_group                       = aws_cloudwatch_log_group.this["${key}.debug"].name
          lua_code                          = replace(trimspace(file("${path.module}/router/${revision}/sanitize.lua")), "\n", " ")
          upload_timeout                    = "1m"
          s3_endpoint_configuration         = ""
          cloudwatch_endpoint_configuration = ""
        })
      }
    }
  ]...)
}

resource "aws_s3_bucket" "this" {
  for_each      = local.buckets
  bucket        = "${var.name}-app-${each.key}-${var.account_id}"
  force_destroy = false
  tags          = var.tags
  lifecycle { prevent_destroy = true }
}

resource "aws_s3_bucket_public_access_block" "this" {
  for_each                = local.buckets
  bucket                  = aws_s3_bucket.this[each.key].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "this" {
  for_each = local.buckets
  bucket   = aws_s3_bucket.this[each.key].id
  rule { object_ownership = "BucketOwnerEnforced" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  for_each = local.buckets
  bucket   = aws_s3_bucket.this[each.key].id
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "AES256" }
  }
}

resource "aws_s3_bucket_policy" "this" {
  for_each = local.buckets
  bucket   = aws_s3_bucket.this[each.key].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "DenyInsecureTransport", Effect = "Deny", Principal = "*", Action = "s3:*"
      Resource  = [aws_s3_bucket.this[each.key].arn, "${aws_s3_bucket.this[each.key].arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })
}

resource "aws_s3_bucket_versioning" "config" {
  count  = local.provisioned ? 1 : 0
  bucket = aws_s3_bucket.this["config"].id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_lifecycle_configuration" "logs" {
  count  = local.provisioned ? 1 : 0
  bucket = aws_s3_bucket.this["logs"].id
  rule {
    id     = "expire-application-logs"
    status = "Enabled"
    filter { prefix = "env=${var.environment}/" }
    expiration { days = 90 }
    abort_incomplete_multipart_upload { days_after_initiation = 1 }
  }
}

resource "aws_cloudwatch_log_group" "this" {
  for_each          = local.groups
  name              = each.value.name
  retention_in_days = each.value.days
  tags              = var.tags
  lifecycle { prevent_destroy = true }
}

resource "aws_s3_object" "config" {
  for_each               = local.configurations
  bucket                 = aws_s3_bucket.this["config"].id
  key                    = "revisions/${each.value.revision}/${each.value.service}/${sha256(each.value.content)}/fluent-bit.conf"
  content                = each.value.content
  content_type           = "text/plain"
  server_side_encryption = "AES256"
  # Versioning does not make a deleted key readable by an old FireLens ARN.
  lifecycle { prevent_destroy = true }
  depends_on = [aws_s3_bucket_versioning.config, aws_s3_bucket_public_access_block.this, aws_s3_bucket_policy.this]
}

resource "aws_iam_role_policy" "runtime" {
  for_each = local.services
  name     = "${var.name}-${each.key}-archive-logs"
  role     = each.value.runtime_role_name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow", Action = ["s3:PutObject"]
        Resource = ["${aws_s3_bucket.this["logs"].arn}/env=${var.environment}/service=${each.value.service_name}/*"]
      },
      {
        Effect   = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = [for kind in ["ops", "debug"] : "${aws_cloudwatch_log_group.this["${each.key}.${kind}"].arn}:*"]
      },
    ]
  })
}

resource "aws_iam_role_policy" "execution" {
  for_each = local.services
  name     = "${var.name}-${each.key}-read-log-config"
  role     = each.value.execution_role_name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow", Action = ["s3:GetObject"]
        Resource = ["${aws_s3_bucket.this["config"].arn}/revisions/*/${each.key}/*"]
      },
      { Effect = "Allow", Action = ["s3:GetBucketLocation"], Resource = [aws_s3_bucket.this["config"].arn] },
    ]
  })
}
