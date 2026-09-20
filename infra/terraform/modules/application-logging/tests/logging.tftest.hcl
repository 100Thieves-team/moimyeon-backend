mock_provider "aws" {}

variables {
  name        = "moimyeon-test"
  environment = "dev"
  region      = "ap-northeast-2"
  account_id  = "123456789012"
  services = {
    api = {
      container_name    = "core-api", service_name = "core-api"
      runtime_role_name = "api-runtime", execution_role_name = "api-execution"
    }
    worker = {
      container_name    = "core-worker", service_name = "core-worker"
      runtime_role_name = "worker-runtime", execution_role_name = "worker-execution"
    }
  }
}

run "disabled_has_no_resources" {
  command = plan
  assert {
    condition     = length(aws_s3_bucket.this) == 0 && length(aws_cloudwatch_log_group.this) == 0 && length(aws_iam_role_policy.runtime) == 0
    error_message = "Disabled logging must not create storage, groups, or permissions."
  }
  assert {
    condition     = length(output.routers) == 0
    error_message = "Disabled logging must not provide a router."
  }
}

run "enabled_retention_and_budgets" {
  command = plan
  variables { mode = "enabled" }
  assert {
    condition     = length(aws_s3_bucket.this) == 2 && length(aws_cloudwatch_log_group.this) == 8
    error_message = "Two independent buckets and per-service ops/debug/router/fallback groups are required."
  }
  assert {
    condition     = aws_cloudwatch_log_group.this["api.debug"].retention_in_days == 3 && aws_cloudwatch_log_group.this["worker.ops"].retention_in_days == 7
    error_message = "Debug and operational retention must be separate."
  }
  assert {
    condition     = one(aws_s3_bucket_lifecycle_configuration.logs[0].rule).expiration[0].days == 90
    error_message = "Archive expiry must be 90 days."
  }
  assert {
    condition     = output.routers["api"].cpu == 64 && output.routers["api"].memory == 128 && output.extra_memory == 160
    error_message = "Task budget must include router and driver overhead."
  }
  assert {
    condition     = !output.routers["api"].essential && output.routers["api"].restartPolicy.enabled
    error_message = "Router exit must not directly terminate the app."
  }
  assert {
    condition     = output.routers["api"].firelensConfiguration.options.config-file-type == "s3" && output.app_log_configurations["api"].logDriver == "awsfirelens"
    error_message = "ECS must route logs using an external versioned configuration."
  }
  assert {
    condition     = can(regex("@sha256:[0-9a-f]{64}$", output.routers["api"].image))
    error_message = "The router image must be immutable."
  }
  assert {
    condition     = contains(keys(aws_s3_object.config), "api.v1") && contains(keys(aws_s3_object.config), "worker.v1")
    error_message = "Versioned configuration objects must be retained for each service."
  }
  assert {
    condition = alltrue([for bucket in aws_s3_bucket_public_access_block.this :
      bucket.block_public_acls && bucket.block_public_policy && bucket.ignore_public_acls && bucket.restrict_public_buckets
    ])
    error_message = "Config and log buckets must block all public access."
  }
  assert {
    condition = alltrue([for encryption in aws_s3_bucket_server_side_encryption_configuration.this :
      one(one(encryption.rule).apply_server_side_encryption_by_default).sse_algorithm == "AES256"
    ])
    error_message = "Both buckets must encrypt stored objects."
  }

}

run "provision_retains_storage" {
  command = plan
  variables { mode = "provision" }
  assert {
    condition     = length(aws_s3_object.config) == 2 && length(aws_iam_role_policy.execution) == 2
    error_message = "Stopping routing must retain old configuration and read permissions for rollback."
  }
  assert {
    condition     = aws_cloudwatch_log_group.this["api.fallback"].retention_in_days == 3 && output.fallback_log_configurations["api"].options.mode == "non-blocking"
    error_message = "Stopping the router must not send debug to legacy 30-day groups or block stdout."
  }

}

run "invalid_mode" {
  command = plan
  variables { mode = "invalid" }
  expect_failures = [var.mode]
}
