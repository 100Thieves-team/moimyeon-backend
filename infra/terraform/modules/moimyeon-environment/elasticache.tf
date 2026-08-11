resource "random_password" "notification_redis_auth" {
  count = var.enable_notification_redis ? 1 : 0

  length      = 48
  special     = false
  min_lower   = 2
  min_numeric = 2
  min_upper   = 2
}

resource "aws_elasticache_subnet_group" "notification" {
  count = var.enable_notification_redis ? 1 : 0

  name       = "${local.name}-notification"
  subnet_ids = aws_subnet.private_db[*].id

  tags = local.tags
}

resource "aws_elasticache_replication_group" "notification" {
  count = var.enable_notification_redis ? 1 : 0

  replication_group_id = "${local.name}-notification"
  description          = "Notification Stream and relay coordination for ${local.name}"

  engine                     = "valkey"
  node_type                  = var.notification_redis_node_type
  num_cache_clusters         = var.notification_redis_node_count
  port                       = 6379
  subnet_group_name          = aws_elasticache_subnet_group.notification[0].name
  security_group_ids         = [aws_security_group.notification_redis[0].id]
  auth_token                 = random_password.notification_redis_auth[0].result
  automatic_failover_enabled = var.notification_redis_node_count > 1
  multi_az_enabled           = var.notification_redis_node_count > 1
  transit_encryption_enabled = true
  transit_encryption_mode    = "required"
  snapshot_retention_limit   = var.notification_redis_snapshot_retention_days
  snapshot_window            = "17:00-18:00"
  maintenance_window         = "sun:18:00-sun:19:00"
  apply_immediately          = var.environment != "live"

  lifecycle {
    precondition {
      condition     = var.environment != "live" || var.notification_redis_node_count >= 2
      error_message = "The live notification Valkey replication group requires at least two nodes."
    }
  }

  tags = local.tags
}

resource "aws_ssm_parameter" "notification_redis_url" {
  count = var.enable_notification_redis ? 1 : 0

  name        = "/${var.project}/${var.environment}/shared/STORAGE_REDIS_URL"
  description = "TLS notification Valkey URL shared by core-api and core-worker in ${local.name}"
  type        = "SecureString"
  value       = "rediss://:${random_password.notification_redis_auth[0].result}@${aws_elasticache_replication_group.notification[0].primary_endpoint_address}:6379"

  tags = local.tags
}
