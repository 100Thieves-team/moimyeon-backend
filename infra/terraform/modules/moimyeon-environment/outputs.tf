output "environment" {
  description = "Deployment environment."
  value       = var.environment
}

output "aws_region" {
  description = "AWS region for this environment."
  value       = data.aws_region.current.name
}

output "ecr_repository_url" {
  description = "ECR repository URL for core-api images."
  value       = aws_ecr_repository.app.repository_url
}

output "notification_worker_ecr_repository_url" {
  description = "ECR repository URL for core-worker images."
  value       = aws_ecr_repository.notification_worker.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name."
  value       = aws_ecs_cluster.this.name
}

output "ecs_service_name" {
  description = "ECS service name."
  value       = aws_ecs_service.app.name
}

output "ecs_container_name" {
  description = "ECS container name used by the deploy workflow."
  value       = var.container_name
}

output "notification_worker_ecs_service_name" {
  description = "Notification worker ECS service name."
  value       = aws_ecs_service.notification_worker.name
}

output "notification_worker_ecs_container_name" {
  description = "Notification worker ECS container name."
  value       = var.notification_worker_container_name
}

output "github_deploy_role_arn" {
  description = "GitHub Actions deploy role ARN for this environment."
  value       = aws_iam_role.github_deploy.arn
}

output "alb_dns_name" {
  description = "ALB DNS name."
  value       = aws_lb.app.dns_name
}

output "alb_access_log_bucket_name" {
  description = "S3 bucket receiving ALB access logs."
  value       = aws_s3_bucket.alb_access_logs.bucket
}

output "waf_web_acl_arn" {
  description = "Regional WAF web ACL associated with the ALB."
  value       = aws_wafv2_web_acl.app.arn
}

output "waf_log_group_name" {
  description = "CloudWatch Logs group receiving WAF request logs."
  value       = aws_cloudwatch_log_group.waf.name
}

output "app_url" {
  description = "Application URL. Custom domain when configured, otherwise ALB HTTP DNS."
  value = local.app_domain_enabled ? (
    local.https_enabled ? "https://${var.app_domain_name}" : "http://${var.app_domain_name}"
  ) : "http://${aws_lb.app.dns_name}"
}

output "rds_endpoint" {
  description = "RDS endpoint address."
  value       = aws_db_instance.core.address
}

output "notification_redis_endpoint" {
  description = "Private Cloud Map DNS name of the notification Redis ECS service, if enabled."
  value       = var.enable_notification_redis ? local.notification_redis_host : null
}

output "notification_redis_url_parameter_name" {
  description = "SSM parameter containing the private notification Redis URL, if enabled."
  value       = var.enable_notification_redis ? local.notification_redis_url_param_name : null
}

output "notification_redis_password_parameter_name" {
  description = "Pre-created SSM SecureString expected to contain the notification Redis password."
  value       = local.notification_redis_password_param_name
}

output "upload_bucket_name" {
  description = "S3 upload bucket name."
  value       = aws_s3_bucket.uploads.bucket
}

output "ssm_parameter_prefix" {
  description = "SSM parameter prefix used by the ECS task."
  value       = "/${var.project}/${var.environment}/core-api"
}

output "image_uri_parameter_name" {
  description = "SSM parameter the deploy workflow updates with the last deployed image URI."
  value       = aws_ssm_parameter.image_uri.name
}

output "notification_worker_image_uri_parameter_name" {
  description = "SSM parameter updated with the last deployed core-worker image URI."
  value       = aws_ssm_parameter.notification_worker_image_uri.name
}

output "firebase_service_account_parameter_name" {
  description = "Pre-created SSM SecureString expected to contain the Firebase service account JSON."
  value       = local.firebase_service_account_param_name
}

output "gmail_app_password_parameter_name" {
  description = "Pre-created SSM SecureString expected to contain the Gmail app password."
  value       = local.gmail_app_password_param_name
}

output "db_bastion_instance_id" {
  description = "DB access bastion instance ID (SSM port-forward target), if enabled."
  value       = var.enable_db_bastion ? aws_instance.db_bastion[0].id : null
}

output "external_dns_records" {
  description = "DNS records to create manually when dns_management is external."
  value = local.external_dns_enabled ? {
    app = {
      type  = "CNAME"
      name  = var.app_domain_name
      value = aws_lb.app.dns_name
    }
    acm_validation = [
      for dvo in aws_acm_certificate.app[0].domain_validation_options : {
        domain = dvo.domain_name
        type   = dvo.resource_record_type
        name   = dvo.resource_record_name
        value  = dvo.resource_record_value
      }
    ]
  } : null
}

output "ecs_task_definition_arn" {
  description = "Terraform-managed Core API task definition template ARN."
  value       = aws_ecs_task_definition.app.arn
}

output "notification_worker_task_definition_arn" {
  description = "Terraform-managed Notification Worker task definition template ARN."
  value       = aws_ecs_task_definition.notification_worker.arn
}
