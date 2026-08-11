output "github_deploy_role_arn" {
  description = "GitHub Actions role ARN for dev."
  value       = module.dev.github_deploy_role_arn
}

output "aws_region" {
  description = "AWS region."
  value       = module.dev.aws_region
}

output "ecr_repository_url" {
  description = "ECR repository URL."
  value       = module.dev.ecr_repository_url
}

output "notification_worker_ecr_repository_url" {
  description = "ECR repository URL for core-worker."
  value       = module.dev.notification_worker_ecr_repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name."
  value       = module.dev.ecs_cluster_name
}

output "ecs_service_name" {
  description = "ECS service name."
  value       = module.dev.ecs_service_name
}

output "ecs_container_name" {
  description = "ECS container name."
  value       = module.dev.ecs_container_name
}

output "notification_worker_ecs_service_name" {
  description = "Notification worker ECS service name."
  value       = module.dev.notification_worker_ecs_service_name
}

output "notification_worker_ecs_container_name" {
  description = "Notification worker ECS container name."
  value       = module.dev.notification_worker_ecs_container_name
}

output "app_url" {
  description = "Application URL."
  value       = module.dev.app_url
}

output "alb_dns_name" {
  description = "ALB DNS name."
  value       = module.dev.alb_dns_name
}

output "rds_endpoint" {
  description = "RDS endpoint address."
  value       = module.dev.rds_endpoint
}

output "notification_redis_endpoint" {
  description = "Private DNS name of the notification Redis ECS service."
  value       = module.dev.notification_redis_endpoint
}

output "notification_redis_url_parameter_name" {
  description = "Pre-created SSM SecureString name expected for the notification Redis URL."
  value       = module.dev.notification_redis_url_parameter_name
}

output "notification_redis_password_parameter_name" {
  description = "Pre-created SSM SecureString name expected for the notification Redis password."
  value       = module.dev.notification_redis_password_parameter_name
}

output "upload_bucket_name" {
  description = "S3 upload bucket."
  value       = module.dev.upload_bucket_name
}

output "image_uri_parameter_name" {
  description = "SSM parameter updated by the deploy workflow."
  value       = module.dev.image_uri_parameter_name
}

output "notification_worker_image_uri_parameter_name" {
  description = "SSM parameter updated with the deployed core-worker image URI."
  value       = module.dev.notification_worker_image_uri_parameter_name
}

output "firebase_service_account_parameter_name" {
  description = "SSM SecureString name expected for Firebase service account JSON."
  value       = module.dev.firebase_service_account_parameter_name
}

output "gmail_app_password_parameter_name" {
  description = "SSM SecureString name expected for Gmail app password."
  value       = module.dev.gmail_app_password_parameter_name
}

output "db_bastion_instance_id" {
  description = "DB access bastion instance ID (SSM port-forward target)."
  value       = module.dev.db_bastion_instance_id
}

output "external_dns_records" {
  description = "Manual DNS records for dns_management = external."
  value       = module.dev.external_dns_records
}
