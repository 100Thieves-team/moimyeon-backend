output "monitoring_instance_id" {
  description = "Private monitoring host ID for AWS-StartPortForwardingSession (Grafana port 3000)."
  value       = try(aws_instance.monitoring[0].id, null)
}

output "monitoring_otlp_metrics_url" {
  description = "Private OTLP HTTP metrics receiver. Accessible only from API/Worker task security groups."
  value       = var.enable_monitoring ? local.monitoring_otlp_metrics_url : null
}

output "monitoring_data_volume_id" {
  description = "Retained monitoring data volume. Snapshot before changes that affect its AZ or size."
  value       = try(aws_ebs_volume.monitoring_data[0].id, null)
}

output "monitoring_grafana_password_parameter_name" {
  description = "Pre-created Grafana SecureString expected at runtime; Terraform never reads its value."
  value       = var.enable_monitoring ? local.monitoring_grafana_parameter_name : null
}

output "monitoring_sentry_parameter_names" {
  description = "Pre-created API and Worker Sentry DSN SecureString names; values are not Terraform-owned."
  value       = var.enable_monitoring ? [local.sentry_api_parameter_name, local.sentry_worker_parameter_name] : []
}
