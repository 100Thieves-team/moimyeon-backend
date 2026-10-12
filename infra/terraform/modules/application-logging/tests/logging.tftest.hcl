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
  # Bucket ARNs are computed; fix them so the router's config path is known at plan.
  override_resource {
    target          = aws_s3_bucket.this
    override_during = plan
    values          = { arn = "arn:aws:s3:::moimyeon-test-app-config-123456789012" }
  }
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
    condition = alltrue([for router in values(output.routers) :
      router.user == "0" && router.portMappings == [] && router.systemControls == [] && router.volumesFrom == []
    ])
    error_message = "The router must state the defaults ECS stores, or every plan replaces the task definition (MOI-581)."
  }
  assert {
    condition = alltrue([for service in ["api", "worker"] :
      alltrue([for revision in ["v1", "v2", "v3"] : contains(keys(aws_s3_object.config), "${service}.${revision}")])
    ])
    error_message = "Versioned configuration objects must be retained for each service, so older task definitions can still start."
  }
  assert {
    condition = alltrue([for service in ["api", "worker"] :
      endswith(output.routers[service].firelensConfiguration.options.config-file-value, "/${aws_s3_object.config["${service}.v3"].key}")
    ])
    error_message = "New task definitions must read the v3 router, which forwards growth fields (MOI-521)."
  }
  assert {
    condition = alltrue([for key, router in output.routers :
      !contains(keys(router), "secrets") && !strcontains(aws_s3_object.config["${key}.v3"].content, "/i/v0/e/")
    ])
    error_message = "Without PostHog delivery the router must not receive a token or send anywhere new."
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
    condition     = length(aws_s3_object.config) == 6 && length(aws_iam_role_policy.execution) == 2
    error_message = "Stopping routing must retain every router revision (v1, v2, v3 per service) and read permissions for rollback."
  }
  assert {
    condition     = aws_cloudwatch_log_group.this["api.fallback"].retention_in_days == 3 && output.fallback_log_configurations["api"].options.mode == "non-blocking"
    error_message = "Stopping the router must not send debug to legacy 30-day groups or block stdout."
  }

}

run "posthog_delivery_only_for_enabled_service" {
  command = plan
  variables {
    mode    = "enabled"
    posthog = { project_token_parameter_arn = "arn:aws:ssm:ap-northeast-2:123456789012:parameter/moimyeon/dev/log-router/POSTHOG_PROJECT_TOKEN" }
    services = {
      api = {
        container_name    = "core-api", service_name = "core-api"
        runtime_role_name = "api-runtime", execution_role_name = "api-execution"
        posthog_enabled   = true
      }
      worker = {
        container_name    = "core-worker", service_name = "core-worker"
        runtime_role_name = "worker-runtime", execution_role_name = "worker-execution"
      }
    }
  }
  override_resource {
    target          = aws_s3_bucket.this
    override_during = plan
    values          = { arn = "arn:aws:s3:::moimyeon-test-app-config-123456789012" }
  }
  assert {
    condition     = output.routers["api"].secrets == [{ name = "POSTHOG_PROJECT_TOKEN", valueFrom = "arn:aws:ssm:ap-northeast-2:123456789012:parameter/moimyeon/dev/log-router/POSTHOG_PROJECT_TOKEN" }]
    error_message = "The api router must read the PostHog token from SSM by ARN."
  }
  assert {
    condition     = !contains(keys(output.routers["worker"]), "secrets") && !strcontains(aws_s3_object.config["worker.v3"].content, "/i/v0/e/")
    error_message = "Only the service that writes growth events may send to PostHog."
  }
  assert {
    condition = alltrue([for fragment in ["Match         posthog.*", "Host          us.i.posthog.com", "Port          443", "URI           /i/v0/e/", "tls.verify On", "call          posthog"] :
      strcontains(aws_s3_object.config["api.v3"].content, fragment)
    ])
    error_message = "The api v3 router must send growth copies to the PostHog US capture endpoint over verified TLS."
  }
  assert {
    condition     = !strcontains(aws_s3_object.config["api.v3"].content, "parameter/moimyeon")
    error_message = "The router configuration must not carry the token parameter; the container receives it as a secret."
  }
  assert {
    condition     = alltrue([for revision in ["v1", "v2"] : !strcontains(aws_s3_object.config["api.${revision}"].content, "/i/v0/e/")])
    error_message = "Deployed revisions must not change when PostHog is enabled."
  }
}

run "posthog_service_requires_settings" {
  command = plan
  variables {
    mode = "enabled"
    services = {
      api = {
        container_name    = "core-api", service_name = "core-api"
        runtime_role_name = "api-runtime", execution_role_name = "api-execution"
        posthog_enabled   = true
      }
    }
  }
  expect_failures = [var.services]
}

run "invalid_mode" {
  command = plan
  variables { mode = "invalid" }
  expect_failures = [var.mode]
}
