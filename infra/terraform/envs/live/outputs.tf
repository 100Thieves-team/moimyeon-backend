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

output "app_url" {
  description = "Application URL."
  value       = module.live.app_url
}

output "alb_dns_name" {
  description = "ALB DNS name."
  value       = module.live.alb_dns_name
}

output "rds_endpoint" {
  description = "RDS endpoint address."
  value       = module.live.rds_endpoint
}

output "upload_bucket_name" {
  description = "S3 upload bucket."
  value       = module.live.upload_bucket_name
}

output "image_uri_parameter_name" {
  description = "SSM parameter updated by the deploy workflow."
  value       = module.live.image_uri_parameter_name
}

output "db_bastion_instance_id" {
  description = "DB access bastion instance ID (SSM port-forward target)."
  value       = module.live.db_bastion_instance_id
}

output "external_dns_records" {
  description = "Manual DNS records for dns_management = external."
  value       = module.live.external_dns_records
}
