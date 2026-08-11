variable "project" {
  description = "Project slug used in resource names and tags."
  type        = string
  default     = "moimyeon"
}

variable "environment" {
  description = "Deployment environment."
  type        = string

  validation {
    condition     = contains(["dev", "live"], var.environment)
    error_message = "environment must be dev or live."
  }
}

variable "github_repository" {
  description = "GitHub repository allowed to deploy this environment, in owner/repo form."
  type        = string
}

variable "github_branch" {
  description = "Branch allowed to assume this environment's GitHub Actions deploy role."
  type        = string
}

variable "github_oidc_provider_arn" {
  description = "Shared GitHub Actions OIDC provider ARN from envs/shared (or the account's existing provider)."
  type        = string
}

variable "github_deploy_immutable_repo" {
  description = "GitHub immutable OIDC subject prefix (org@orgid/repo@repoid) — GitHub now issues subs with numeric IDs. When set, the deploy role also trusts repo:<this>:ref:refs/heads/<branch>. Get it from: gh api /repos/<owner>/<repo>/actions/oidc/customization/sub (sub_claim_prefix)."
  type        = string
  default     = null
}

# ---------------------------------------------------------------------------
# DNS / TLS
# ---------------------------------------------------------------------------

variable "route53_zone_id" {
  description = "Public Route 53 hosted zone ID for app_domain_name. Required when app_domain_name is set unless route53_zone_name can be looked up."
  type        = string
  default     = null
}

variable "route53_zone_name" {
  description = "Public Route 53 hosted zone name for lookup when route53_zone_id is null."
  type        = string
  default     = null
}

variable "app_domain_name" {
  description = "FQDN pointed to the ALB, e.g. api.dev.moimyeon.plady.io. Null skips ACM/Route53 and exposes only ALB DNS."
  type        = string
  default     = null
}

variable "dns_management" {
  description = "DNS mode for app_domain_name: route53 (Terraform-managed), external (manual DNS such as Cloudflare/내도메인.한국), or none (ALB DNS/HTTP only)."
  type        = string
  default     = "route53"

  validation {
    condition     = contains(["route53", "external", "none"], var.dns_management)
    error_message = "dns_management must be route53, external, or none."
  }
}

variable "enable_https" {
  description = "Create the HTTPS listener. For external DNS, apply once with false, create the printed ACM CNAME record, then set true and apply again."
  type        = bool
  default     = true
}

# ---------------------------------------------------------------------------
# Network
# ---------------------------------------------------------------------------

variable "vpc_cidr" {
  description = "CIDR block for the environment VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "az_count" {
  description = "Number of availability zones to use (ignored when availability_zones is set)."
  type        = number
  default     = 2
}

variable "availability_zones" {
  description = "Explicit AZ list. Empty uses the first az_count available AZs. Set to match existing subnets when importing (dev uses 2a/2c)."
  type        = list(string)
  default     = []
}

variable "enable_nat_gateway" {
  description = "Create one NAT Gateway so ECS instances in private subnets can pull images and reach AWS APIs."
  type        = bool
  default     = true
}

variable "enable_s3_gateway_endpoint" {
  description = "Create an S3 Gateway VPC endpoint on the private-app route table (matches MOI-361 vpce-02c192c984d8874df)."
  type        = bool
  default     = true
}

variable "enable_notification_redis" {
  description = "Provision the private ElastiCache Valkey replication group used by notification relay and workers."
  type        = bool
  default     = false
}

variable "notification_redis_node_type" {
  description = "ElastiCache node type for the notification Valkey replication group."
  type        = string
  default     = "cache.t4g.micro"
}

variable "notification_redis_node_count" {
  description = "Number of nodes in the notification Valkey replication group. Use at least 2 in live for automatic failover."
  type        = number
  default     = 1

  validation {
    condition     = var.notification_redis_node_count >= 1 && var.notification_redis_node_count <= 6
    error_message = "notification_redis_node_count must be between 1 and 6."
  }
}

variable "notification_redis_snapshot_retention_days" {
  description = "Number of days to retain automatic notification Valkey snapshots."
  type        = number
  default     = 1

  validation {
    condition     = var.notification_redis_snapshot_retention_days >= 0 && var.notification_redis_snapshot_retention_days <= 35
    error_message = "notification_redis_snapshot_retention_days must be between 0 and 35."
  }
}

# Security group descriptions are immutable in AWS (changing forces replacement).
# Override to the existing value when importing a hand-built SG.
variable "alb_sg_description" {
  description = "ALB security group description (immutable)."
  type        = string
  default     = "Public ALB ingress"
}

variable "rds_sg_description" {
  description = "RDS security group description (immutable)."
  type        = string
  default     = "RDS MySQL"
}

# ---------------------------------------------------------------------------
# ECS capacity (EC2)
# ---------------------------------------------------------------------------

variable "ecs_instance_type" {
  description = "EC2 instance type for ECS capacity."
  type        = string
  default     = "t3.small"
}

variable "ecs_min_size" {
  description = "Minimum ECS EC2 Auto Scaling Group capacity."
  type        = number
  default     = 1
}

variable "ecs_max_size" {
  description = "Maximum ECS EC2 Auto Scaling Group capacity."
  type        = number
  default     = 2
}

variable "ecs_desired_capacity" {
  description = "Desired ECS EC2 Auto Scaling Group capacity."
  type        = number
  default     = 1
}

variable "ecs_instance_root_volume_size" {
  description = "Root EBS volume size in GiB for ECS EC2 instances."
  type        = number
  default     = 30
}

# ---------------------------------------------------------------------------
# ECS service / task
# ---------------------------------------------------------------------------

variable "ecs_service_desired_count" {
  description = "Desired ECS service task count."
  type        = number
  default     = 2
}

variable "ecs_service_min_count" {
  description = "Minimum ECS service task count for Application Auto Scaling."
  type        = number
  default     = 1
}

variable "ecs_service_max_count" {
  description = "Maximum ECS service task count for Application Auto Scaling."
  type        = number
  default     = 4
}

variable "ecs_service_cpu_target" {
  description = "Average ECS service CPU percentage target."
  type        = number
  default     = 60
}

variable "enable_ecs_exec" {
  description = "Enable ECS Exec for the service (SSM-based shell into tasks)."
  type        = bool
  default     = true
}

variable "container_name" {
  description = "ECS container name (also used by the deploy workflow)."
  type        = string
  default     = "core-api"
}

variable "container_port" {
  description = "Container HTTP port."
  type        = number
  default     = 8080
}

variable "container_image_tag" {
  description = "Initial image tag for the Terraform task definition. GitHub Actions registers SHA-tagged revisions after bootstrap."
  type        = string
  default     = null
}

variable "task_cpu" {
  description = "ECS task CPU units."
  type        = number
  default     = 512
}

variable "task_memory" {
  description = "ECS task memory in MiB. Keep 2 tasks within the ECS instance memory (t3.small = ~2 GiB)."
  type        = number
  default     = 768
}

variable "health_check_path" {
  description = "ALB (and optional container) readiness path. The core-api group includes its DB but excludes notification Redis."
  type        = string
  default     = "/actuator/health/readiness"
}

variable "target_group_name" {
  description = "ALB target group name. Null uses <name>-tg-app. Override when a same-named instance-type TG already exists (blue/green: use a distinct name, e.g. <name>-tg-ecs)."
  type        = string
  default     = null
}

variable "manage_alb_listeners" {
  description = "Create/own the ALB HTTP/HTTPS listeners. false = leave existing listeners untouched (pre-cutover): the ECS target group is created and health-checked but receives no live traffic until the listener is switched over."
  type        = bool
  default     = true
}

variable "provisional_ecs_listener_port" {
  description = "When set, create an HTTP listener on this port forwarding to the ECS target group. ECS requires a TG to be attached to a listener before a service can use it; this provides that during a pre-cutover absorb (manage_alb_listeners=false) without touching the live :80/:443 listeners. Not opened in the ALB SG, so it is health-check/internal only."
  type        = number
  default     = null
}

variable "enable_container_health_check" {
  description = "Add a container-level Docker HEALTHCHECK. Off by default: the eclipse-temurin JRE image has no wget/curl, so rollout health is judged by the ALB target group instead."
  type        = bool
  default     = false
}

variable "container_health_check_start_period_seconds" {
  description = "Seconds to ignore container health check failures while the app starts (only used when enable_container_health_check)."
  type        = number
  default     = 120
}

variable "ecs_service_health_check_grace_period_seconds" {
  description = "Seconds for ECS to ignore ALB target health failures during a deployment."
  type        = number
  default     = 240
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention in days."
  type        = number
  default     = 30
}

variable "additional_environment" {
  description = "Additional non-sensitive container environment variables (e.g. MOI-361 STORAGE_OBJECTSTORAGE_S3_* keys once merged)."
  type        = map(string)
  default     = {}
}

# ---------------------------------------------------------------------------
# Notification worker service / task
# ---------------------------------------------------------------------------

variable "notification_worker_container_name" {
  description = "Notification worker ECS container and service name."
  type        = string
  default     = "core-worker"
}

variable "notification_worker_desired_count" {
  description = "Desired notification worker count. Keep zero until vendor secrets have been created in SSM."
  type        = number
  default     = 0
}

variable "notification_worker_task_cpu" {
  description = "Notification worker task CPU units."
  type        = number
  default     = 256
}

variable "notification_worker_task_memory" {
  description = "Notification worker task memory in MiB."
  type        = number
  default     = 512
}

variable "notification_worker_image_tag" {
  description = "Initial notification worker image tag. GitHub Actions registers SHA-tagged revisions after bootstrap."
  type        = string
  default     = null
}

variable "firebase_project_id" {
  description = "Firebase project ID used by the notification worker."
  type        = string
  default     = null
}

variable "notification_web_push_action_base_url" {
  description = "Frontend base URL opened when a web push notification is clicked."
  type        = string
  default     = null
}

variable "notification_email_ses_from_address" {
  description = "Verified SES sender address used by the notification worker."
  type        = string
  default     = null
}

variable "notification_email_gmail_address" {
  description = "Gmail or Google Workspace address used as the email fallback."
  type        = string
  default     = null
}

# ---------------------------------------------------------------------------
# Database (MySQL)
# ---------------------------------------------------------------------------

variable "rds_instance_class" {
  description = "RDS MySQL instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "mysql_engine_version" {
  description = "MySQL engine version. Null lets AWS choose the default current version."
  type        = string
  default     = "8.4"
}

variable "db_name" {
  description = "MySQL database name."
  type        = string
  default     = "moimyeon"
}

variable "db_username" {
  description = "MySQL master username."
  type        = string
  default     = "moimyeon"
}

variable "db_allocated_storage" {
  description = "Initial RDS storage in GiB."
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "Maximum RDS autoscaled storage in GiB."
  type        = number
  default     = 100
}

variable "db_backup_retention_period" {
  description = "RDS automated backup retention in days."
  type        = number
  default     = 7
}

variable "db_deletion_protection" {
  description = "Protect RDS from deletion."
  type        = bool
  default     = true
}

variable "db_skip_final_snapshot" {
  description = "Skip final RDS snapshot on destroy."
  type        = bool
  default     = false
}

variable "generate_db_password" {
  description = "true: Terraform generates the DB password, writes it to SSM, and sets it as the RDS master password (fresh envs). false: reference an existing SSM SecureString (pre-populated with the current password) and never touch the RDS password — for absorbing an existing DB without rotating it."
  type        = bool
  default     = true
}

# ---------------------------------------------------------------------------
# DB access bastion (developer SSM port-forward to private RDS)
# ---------------------------------------------------------------------------

variable "enable_db_bastion" {
  description = "Create the private SSM-managed bastion used to tunnel to RDS (matches moimyeon-dev-role-ssm-db-access)."
  type        = bool
  default     = true
}

variable "extra_rds_ingress_security_group_ids" {
  description = "Additional SG IDs allowed to reach RDS on 3306. Transitional: keep the existing app-host / bastion SGs allowed during a blue/green absorb so the current container keeps its DB connection. Remove after cutover."
  type        = list(string)
  default     = []
}

variable "bastion_instance_type" {
  description = "Instance type for the DB access bastion."
  type        = string
  default     = "t3.micro"
}

# ---------------------------------------------------------------------------
# Application secrets / config
# ---------------------------------------------------------------------------

variable "jwt_issuer" {
  description = "JWT issuer (non-secret; injected only if the app reads JWT_ISSUER)."
  type        = string
  default     = null
}

variable "oauth_google_client_id" {
  description = "Google OAuth client ID (required for the app to boot). Non-secret."
  type        = string
}

variable "oauth_google_client_secret" {
  description = "Google OAuth client secret (required for the app to boot). Stored in SSM SecureString."
  type        = string
  sensitive   = true
}

# ---------------------------------------------------------------------------
# S3 uploads (MOI-361)
# ---------------------------------------------------------------------------

variable "upload_bucket_name" {
  description = "Optional S3 upload bucket name. Null generates moimyeon-<env>-uploads."
  type        = string
  default     = null
}

variable "upload_cors_allowed_origins" {
  description = "Frontend origins allowed to use S3 presigned upload/download URLs."
  type        = list(string)
  default     = []
}

variable "force_destroy_upload_bucket" {
  description = "Allow Terraform destroy to delete a non-empty upload bucket. Prefer false for live."
  type        = bool
  default     = false
}

# ---------------------------------------------------------------------------
# ECR
# ---------------------------------------------------------------------------

variable "ecr_repository_name" {
  description = "ECR repository name. Defaults to moimyeon-<env>-core-api. Set to moimyeon/backend to reuse the pre-existing repo."
  type        = string
  default     = null
}

variable "notification_worker_ecr_repository_name" {
  description = "ECR repository name for core-worker images. Defaults to moimyeon-<env>-core-worker."
  type        = string
  default     = null
}

variable "ecr_image_retention_count" {
  description = "Number of recent ECR images to retain."
  type        = number
  default     = 30
}

variable "tags" {
  description = "Extra tags applied to environment resources."
  type        = map(string)
  default     = {}
}
