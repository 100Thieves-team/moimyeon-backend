variable "enable_monitoring" {
  description = "Enable the dev-only private Prometheus/Grafana host and application OTLP/Sentry integration. Pre-create the documented SSM secrets first."
  type        = bool
  default     = false
}

variable "monitoring_instance_type" {
  description = "x86_64 monitoring host type. The bounded single-node stack is for dev, not HA."
  type        = string
  default     = "t3.small"

  validation {
    condition     = contains(["t3.small", "t3.medium", "t3.large"], var.monitoring_instance_type)
    error_message = "Use an x86_64 t3.small, t3.medium, or t3.large monitoring host."
  }
}

variable "monitoring_data_volume_size" {
  description = "Retained encrypted gp3 volume size in GiB. Prometheus retention is separately bounded by time and size."
  type        = number
  default     = 20

  validation {
    condition     = var.monitoring_data_volume_size >= 20 && var.monitoring_data_volume_size <= 100 && floor(var.monitoring_data_volume_size) == var.monitoring_data_volume_size
    error_message = "Monitoring data volume size must be an integer between 20 and 100 GiB."
  }
}

variable "monitoring_ami_id" {
  description = "Optional pinned Amazon Linux 2023 x86_64 AMI. Null resolves the public AL2023 image parameter; AMI changes replace the host but retain the data volume."
  type        = string
  default     = null

  validation {
    condition     = var.monitoring_ami_id == null ? true : can(regex("^ami-[0-9a-f]{8,17}$", var.monitoring_ami_id))
    error_message = "monitoring_ami_id must be null or an EC2 AMI ID."
  }
}
