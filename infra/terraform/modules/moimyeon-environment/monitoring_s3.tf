# This bucket contains versioned non-secret configuration only, never logs or
# runtime metrics. Metrics and Grafana state live on the retained EBS volume.
resource "aws_s3_bucket" "monitoring_config" {
  count = var.enable_monitoring ? 1 : 0

  bucket        = local.monitoring_config_bucket_name
  force_destroy = false
  tags          = local.tags
}

resource "aws_s3_bucket_public_access_block" "monitoring_config" {
  count = var.enable_monitoring ? 1 : 0

  bucket                  = aws_s3_bucket.monitoring_config[0].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "monitoring_config" {
  count = var.enable_monitoring ? 1 : 0

  bucket = aws_s3_bucket.monitoring_config[0].id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "monitoring_config" {
  count = var.enable_monitoring ? 1 : 0

  bucket = aws_s3_bucket.monitoring_config[0].id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "monitoring_config" {
  count = var.enable_monitoring ? 1 : 0

  bucket = aws_s3_bucket.monitoring_config[0].id

  versioning_configuration {
    status = "Enabled"
  }
}

data "aws_iam_policy_document" "monitoring_config" {
  count = var.enable_monitoring ? 1 : 0

  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.monitoring_config[0].arn, "${aws_s3_bucket.monitoring_config[0].arn}/*"]

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
}

resource "aws_s3_bucket_policy" "monitoring_config" {
  count = var.enable_monitoring ? 1 : 0

  bucket = aws_s3_bucket.monitoring_config[0].id
  policy = data.aws_iam_policy_document.monitoring_config[0].json
}

resource "aws_s3_object" "monitoring_config" {
  for_each = var.enable_monitoring ? toset(local.monitoring_config_paths) : toset([])

  bucket                 = aws_s3_bucket.monitoring_config[0].id
  key                    = "${local.monitoring_config_prefix}/${each.value}"
  source                 = "${local.monitoring_config_directory}/${each.value}"
  source_hash            = filesha256("${local.monitoring_config_directory}/${each.value}")
  server_side_encryption = "AES256"
  content_type           = endswith(each.value, ".json") ? "application/json" : (endswith(each.value, ".sh") ? "text/x-shellscript" : "application/yaml")
  tags                   = local.tags

  depends_on = [
    aws_s3_bucket_versioning.monitoring_config,
    aws_s3_bucket_public_access_block.monitoring_config,
    aws_s3_bucket_ownership_controls.monitoring_config,
    aws_s3_bucket_server_side_encryption_configuration.monitoring_config,
  ]
}
