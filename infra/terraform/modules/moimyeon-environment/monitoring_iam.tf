resource "aws_iam_role" "monitoring" {
  count = var.enable_monitoring ? 1 : 0

  name               = "${local.name}-monitoring"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json
  tags               = local.tags
}

resource "aws_iam_instance_profile" "monitoring" {
  count = var.enable_monitoring ? 1 : 0

  name = "${local.name}-monitoring"
  role = aws_iam_role.monitoring[0].name
  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "monitoring_ssm" {
  count = var.enable_monitoring ? 1 : 0

  role       = aws_iam_role.monitoring[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "monitoring_runtime" {
  count = var.enable_monitoring ? 1 : 0

  statement {
    sid       = "ReadExactMonitoringRelease"
    actions   = ["s3:GetObject"]
    resources = [for config_path in local.monitoring_config_paths : "${aws_s3_bucket.monitoring_config[0].arn}/${local.monitoring_config_prefix}/${config_path}"]
  }

  statement {
    sid       = "ReadGrafanaBootstrapPassword"
    actions   = ["ssm:GetParameter"]
    resources = [local.monitoring_grafana_parameter_arn]
  }

  # AmazonSSMManagedInstanceCore includes GetParameter(s) on '*'. Restrict that
  # inherited grant so the monitoring host cannot retrieve application secrets.
  statement {
    sid           = "DenyUnrelatedParameterValues"
    effect        = "Deny"
    actions       = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
    not_resources = [local.monitoring_grafana_parameter_arn]
  }
}

resource "aws_iam_role_policy" "monitoring_runtime" {
  count = var.enable_monitoring ? 1 : 0

  name   = "${local.name}-monitoring-runtime"
  role   = aws_iam_role.monitoring[0].id
  policy = data.aws_iam_policy_document.monitoring_runtime[0].json
}
