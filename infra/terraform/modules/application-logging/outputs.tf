output "router_cpu" { value = local.router_cpu }
output "extra_memory" { value = local.router_memory + local.driver_memory }
output "bucket_name" { value = local.provisioned ? aws_s3_bucket.this["logs"].bucket : null }

# awsfirelens 는 Name 과 log-driver-buffer-limit 을 제외한 모든 옵션을 Fluent Bit [OUTPUT] 섹션에 그대로 넘긴다.
# Docker 드라이버 옵션(mode·max-buffer-size)을 여기 두면 Fluent Bit 이 "unknown configuration property" 로
# 초기화에 실패해 사이드카가 죽고, dependsOn HEALTHY 에 묶인 앱 컨테이너는 PENDING 에서 배포가 타임아웃된다.
# non-blocking 버퍼는 awsfirelens 전용 옵션인 log-driver-buffer-limit 하나로 지정한다.
output "app_log_configurations" {
  value = { for key, service in local.services : key => {
    logDriver = "awsfirelens"
    options = {
      Name                    = "null"
      log-driver-buffer-limit = "256"
    }
  } }
}

output "routers" {
  value = { for key, service in local.services : key => {
    name          = "log-router"
    image         = local.router_image
    essential     = false
    cpu           = local.router_cpu
    memory        = local.router_memory
    stopTimeout   = 30
    restartPolicy = { enabled = true, restartAttemptPeriod = 60 }
    mountPoints   = [{ sourceVolume = "log-router-buffer", containerPath = "/buffers", readOnly = false }]
    environment = [
      { name = "LOG_SERVICE_NAME", value = service.service_name },
      { name = "LOG_ENVIRONMENT", value = var.environment },
      { name = "TZ", value = "UTC" },
    ]
    firelensConfiguration = {
      type = "fluentbit"
      options = {
        enable-ecs-log-metadata = "false"
        config-file-type        = "s3"
        config-file-value       = "${aws_s3_bucket.this["config"].arn}/${aws_s3_object.config["${key}.${local.active_revision}"].key}"
      }
    }
    healthCheck = {
      command     = ["CMD-SHELL", "curl -fsS http://127.0.0.1:2020/ >/dev/null || exit 1"]
      interval    = 10
      timeout     = 3
      retries     = 3
      startPeriod = 10
    }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.this["${key}.router"].name
        awslogs-region        = var.region
        awslogs-stream-prefix = "router"
        mode                  = "non-blocking"
        max-buffer-size       = "1m"
      }
    }
  } }
  depends_on = [aws_iam_role_policy.execution, aws_iam_role_policy.runtime]
}

output "fallback_log_configurations" {
  value = { for key, service in local.services : key => {
    logDriver = "awslogs"
    options = {
      awslogs-group         = aws_cloudwatch_log_group.this["${key}.fallback"].name
      awslogs-region        = var.region
      awslogs-stream-prefix = service.container_name
      mode                  = "non-blocking"
      max-buffer-size       = "4m"
    }
  } }
}
