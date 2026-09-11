locals {
  monitoring_hostname               = "monitoring.${local.name}.internal"
  monitoring_otlp_metrics_url       = "http://${local.monitoring_hostname}:4318/v1/metrics"
  monitoring_grafana_parameter_name = "/${var.project}/${var.environment}/monitoring/GRAFANA_ADMIN_PASSWORD"
  monitoring_grafana_parameter_arn  = "arn:aws:ssm:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:parameter${local.monitoring_grafana_parameter_name}"
  monitoring_config_directory       = "${path.module}/../../../observability"
  monitoring_config_bucket_name     = "${local.name}-monitoring-config-${data.aws_caller_identity.current.account_id}"
  monitoring_config_paths = [
    "compose.yaml",
    "otel-collector.yaml",
    "prometheus.yaml",
    "start-monitoring.sh",
    "grafana/provisioning/datasources/prometheus.yaml",
    "grafana/provisioning/dashboards/default.yaml",
    "grafana/dashboards/dev-overview.json",
  ]
  monitoring_config_manifest = [
    for config_path in local.monitoring_config_paths : {
      path   = config_path
      sha256 = filesha256("${local.monitoring_config_directory}/${config_path}")
    }
  ]
  monitoring_config_prefix = "releases/${sha256(jsonencode(local.monitoring_config_manifest))}"
  monitoring_environment = var.enable_monitoring ? {
    MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED = "true"
    MANAGEMENT_OTLP_METRICS_EXPORT_URL     = local.monitoring_otlp_metrics_url
    DEPLOYMENT_ENVIRONMENT                 = var.environment
    SENTRY_ENABLED                         = "true"
    SENTRY_LOGS_ENABLED                    = "false"
  } : {}
}

# Public non-secret OS image parameter only. No SecureString data sources exist
# in this stack; the Grafana password is fetched by the host at runtime.
data "aws_ssm_parameter" "monitoring_ami" {
  count = var.enable_monitoring && var.monitoring_ami_id == null ? 1 : 0

  name            = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
  with_decryption = false
}

resource "aws_security_group" "monitoring" {
  count = var.enable_monitoring ? 1 : 0

  name        = "${local.name}-monitoring"
  description = "Private OTLP receiver; Grafana and Prometheus only via SSM local forwarding"
  vpc_id      = aws_vpc.this.id

  tags = merge(local.tags, { Name = "${local.name}-monitoring-sg" })
}

resource "aws_vpc_security_group_ingress_rule" "monitoring_api_otlp" {
  count = var.enable_monitoring ? 1 : 0

  security_group_id            = aws_security_group.monitoring[0].id
  referenced_security_group_id = aws_security_group.ecs_task.id
  description                  = "Core API to OTLP HTTP metrics receiver"
  ip_protocol                  = "tcp"
  from_port                    = 4318
  to_port                      = 4318
}

resource "aws_vpc_security_group_ingress_rule" "monitoring_worker_otlp" {
  count = var.enable_monitoring ? 1 : 0

  security_group_id            = aws_security_group.monitoring[0].id
  referenced_security_group_id = aws_security_group.notification_worker_task.id
  description                  = "Core Worker to OTLP HTTP metrics receiver"
  ip_protocol                  = "tcp"
  from_port                    = 4318
  to_port                      = 4318
}

resource "aws_vpc_security_group_egress_rule" "monitoring_worker_otlp" {
  count = var.enable_monitoring ? 1 : 0

  security_group_id            = aws_security_group.notification_worker_task.id
  referenced_security_group_id = aws_security_group.monitoring[0].id
  description                  = "Core Worker to monitoring OTLP receiver"
  ip_protocol                  = "tcp"
  from_port                    = 4318
  to_port                      = 4318
}

resource "aws_vpc_security_group_egress_rule" "monitoring_https" {
  count = var.enable_monitoring ? 1 : 0

  security_group_id = aws_security_group.monitoring[0].id
  cidr_ipv4         = "0.0.0.0/0"
  description       = "HTTPS to SSM, S3, package repositories and pinned container registries through existing NAT"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

# The volume is independent of the replaceable host and stays in its original
# AZ. Disabling the flag or changing the AZ must stop at prevent_destroy until a
# human has reviewed retention/backup and explicitly changed this protection.
resource "aws_ebs_volume" "monitoring_data" {
  count = var.enable_monitoring ? 1 : 0

  availability_zone = aws_subnet.private_app[0].availability_zone
  type              = "gp3"
  size              = var.monitoring_data_volume_size
  encrypted         = true

  lifecycle {
    prevent_destroy = true
  }

  tags = merge(local.tags, { Name = "${local.name}-monitoring-data" })
}

resource "aws_instance" "monitoring" {
  count = var.enable_monitoring ? 1 : 0

  ami                         = var.monitoring_ami_id != null ? var.monitoring_ami_id : data.aws_ssm_parameter.monitoring_ami[0].value
  instance_type               = var.monitoring_instance_type
  subnet_id                   = aws_subnet.private_app[0].id
  associate_public_ip_address = false
  vpc_security_group_ids      = [aws_security_group.monitoring[0].id]
  iam_instance_profile        = aws_iam_instance_profile.monitoring[0].name
  user_data_replace_on_change = true
  user_data_base64 = base64gzip(templatefile("${local.monitoring_config_directory}/bootstrap.sh.tftpl", {
    region               = data.aws_region.current.region
    config_bucket        = aws_s3_bucket.monitoring_config[0].bucket
    config_prefix        = local.monitoring_config_prefix
    config_manifest_json = jsonencode(local.monitoring_config_manifest)
    volume_id            = aws_ebs_volume.monitoring_data[0].id
    grafana_parameter    = local.monitoring_grafana_parameter_arn
  }))

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  root_block_device {
    volume_size           = 12
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }

  credit_specification {
    cpu_credits = "standard"
  }

  lifecycle {
    precondition {
      condition     = var.environment == "dev" && var.enable_notification_redis && var.enable_nat_gateway
      error_message = "The initial monitoring stack is dev-only and requires the existing notification private DNS namespace and NAT egress."
    }
  }

  depends_on = [
    aws_s3_object.monitoring_config,
    aws_s3_bucket_policy.monitoring_config,
    aws_iam_role_policy.monitoring_runtime,
    aws_iam_role_policy_attachment.monitoring_ssm,
    aws_route_table_association.private_app,
    aws_vpc_security_group_egress_rule.monitoring_https,
  ]

  tags = merge(local.tags, { Name = "${local.name}-monitoring" })
}

# Attachment is created after the EC2 instance exists. Bootstrap waits for the
# exact NVMe volume ID; it must never silently use the root disk if attachment
# fails. stop_instance_before_detaching avoids a live filesystem detach.
resource "aws_volume_attachment" "monitoring_data" {
  count = var.enable_monitoring ? 1 : 0

  device_name                    = "/dev/sdf"
  volume_id                      = aws_ebs_volume.monitoring_data[0].id
  instance_id                    = aws_instance.monitoring[0].id
  stop_instance_before_detaching = true
  force_detach                   = false
}

# Cloud Map owns this namespace's hosted zone and rejects direct Route53 writes.
# Add a separate service; do not modify the existing Redis service or namespace.
resource "aws_service_discovery_service" "monitoring" {
  count = var.enable_monitoring ? 1 : 0

  name = "monitoring"

  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.notification[0].id
    routing_policy = "MULTIVALUE"

    dns_records {
      ttl  = 30
      type = "A"
    }
  }

  # No custom health publisher exists for this EC2 host. DNS discovery is not a
  # readiness check; Collector health and fresh app heartbeats are checked separately.
  tags = local.tags
}

resource "aws_service_discovery_instance" "monitoring" {
  count = var.enable_monitoring ? 1 : 0

  instance_id = "monitoring"
  service_id  = aws_service_discovery_service.monitoring[0].id

  # A stable registration ID updates the address when the EC2 host is replaced.
  attributes = {
    AWS_INSTANCE_IPV4 = aws_instance.monitoring[0].private_ip
  }
}
