aws_region        = "ap-northeast-2"
github_repository = "100Thieves-team/moimyeon-backend"

# The live stack remains scaled to zero until DNS, secrets, and capacity are ready.
app_domain_name             = "api.moimyeon.plady.io"
dns_management              = "external"
enable_https                = false
upload_cors_allowed_origins = []

vpc_cidr    = "10.30.0.0/16"
db_name     = "moimyeon"
db_username = "moimyeon"
# RDS rotates this admin identity in Secrets Manager. ECS uses db_username and
# the separately pre-created SSM DB_PASSWORD instead.
db_master_username = "moimyeon_admin"

# Commit the production client ID in a reviewed PR before raising API capacity.
oauth_google_client_id = null

notification_worker_desired_count     = 0
firebase_project_id                   = null
notification_web_push_action_base_url = "https://moimyeon.plady.io"
notification_email_ses_from_address   = null
notification_email_gmail_address      = null
