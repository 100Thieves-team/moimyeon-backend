output "github_deploy_role_arn" {
  description = "GitHub Actions role ARN for live."
  value       = module.live.github_deploy_role_arn
}

output "aws_region" {
  description = "AWS region."
  value       = module.live.aws_region
}

output "ecr_repository_url" {
  description = "ECR repository URL."
  value       = module.live.ecr_repository_url
}

output "notification_worker_ecr_repository_url" {
  description = "ECR repository URL for core-worker."
  value       = module.live.notification_worker_ecr_repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name."
  value       = module.live.ecs_cluster_name
}

output "ecs_service_name" {
  description = "ECS service name."
  value       = module.live.ecs_service_name
}

output "ecs_container_name" {
  description = "ECS container name."
  value       = module.live.ecs_container_name
}

output "notification_worker_ecs_service_name" {
  description = "Notification worker ECS service name."
  value       = module.live.notification_worker_ecs_service_name
}

output "notification_worker_ecs_container_name" {
  description = "Notification worker ECS container name."
  value       = module.live.notification_worker_ecs_container_name
}

output "app_url" {
  description = "Application URL."
  value       = module.live.app_url
}

output "alb_dns_name" {
  description = "ALB DNS name."
  value       = module.live.alb_dns_name
}

output "alb_access_log_bucket_name" {
  description = "S3 bucket receiving ALB access logs."
  value       = module.live.alb_access_log_bucket_name
}

output "waf_web_acl_arn" {
  description = "Regional WAF web ACL associated with the ALB."
  value       = module.live.waf_web_acl_arn
}

output "waf_log_group_name" {
  description = "CloudWatch Logs group receiving WAF request logs."
  value       = module.live.waf_log_group_name
}

output "rds_endpoint" {
  description = "RDS endpoint address."
  value       = module.live.rds_endpoint
}

output "notification_redis_endpoint" {
  description = "Private DNS name of the notification Redis ECS service when enabled."
  value       = module.live.notification_redis_endpoint
}

output "notification_redis_url_parameter_name" {
  description = "Pre-created SSM SecureString name expected for the notification Redis URL."
  value       = module.live.notification_redis_url_parameter_name
}

output "notification_redis_password_parameter_name" {
  description = "Pre-created SSM SecureString name expected for the notification Redis password."
  value       = module.live.notification_redis_password_parameter_name
}

output "upload_bucket_name" {
  description = "S3 upload bucket."
  value       = module.live.upload_bucket_name
}

output "image_uri_parameter_name" {
  description = "SSM parameter updated by the deploy workflow."
  value       = module.live.image_uri_parameter_name
}

output "notification_worker_image_uri_parameter_name" {
  description = "SSM parameter updated with the deployed core-worker image URI."
  value       = module.live.notification_worker_image_uri_parameter_name
}

output "firebase_service_account_parameter_name" {
  description = "SSM SecureString name expected for Firebase service account JSON."
  value       = module.live.firebase_service_account_parameter_name
}

output "gmail_app_password_parameter_name" {
  description = "SSM SecureString name expected for Gmail app password."
  value       = module.live.gmail_app_password_parameter_name
}

output "db_bastion_instance_id" {
  description = "DB access bastion instance ID (SSM port-forward target)."
  value       = module.live.db_bastion_instance_id
}

output "external_dns_records" {
  description = "Manual DNS records for dns_management = external."
  value       = module.live.external_dns_records
}

output "ecs_task_definition_arn" {
  description = "Terraform-managed Core API task definition template ARN."
  value       = module.live.ecs_task_definition_arn
}

output "notification_worker_task_definition_arn" {
  description = "Terraform-managed Notification Worker task definition template ARN."
  value       = module.live.notification_worker_task_definition_arn
}
