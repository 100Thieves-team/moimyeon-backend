aws_region        = "ap-northeast-2"
github_repository = "100Thieves-team/moimyeon-backend"

app_domain_name             = "api.moimyeon.plady.io"
dns_management              = "external"
enable_https                = true
upload_cors_allowed_origins = ["https://moimyeon.plady.io"]

vpc_cidr    = "10.30.0.0/16"
db_name     = "moimyeon"
db_username = "moimyeon"
# RDS rotates this admin identity in Secrets Manager. ECS uses db_username and
# the separately pre-created SSM DB_PASSWORD instead.
db_master_username = "moimyeon_admin"

# Commit the production client ID in a reviewed PR before raising API capacity.
oauth_google_client_id = "662774804169-oa16hgudgkgbebkdi6fhtvn42g5e26kg.apps.googleusercontent.com"

notification_worker_desired_count = 1
# Shared with dev (decision 2026-10-08): same Firebase project and Gmail sender.
firebase_project_id                   = "moimyeon-development"
notification_web_push_action_base_url = "https://moimyeon.plady.io"
notification_email_ses_from_address   = "no-reply@moimyeon.plady.io"
notification_email_gmail_address      = "100dodukteam@gmail.com"

application_logging_mode = "enabled"
