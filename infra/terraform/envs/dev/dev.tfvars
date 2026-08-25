aws_region        = "ap-northeast-2"
github_repository = "100Thieves-team/moimyeon-backend"

app_domain_name             = "api.dev.moimyeon.plady.io"
dns_management              = "external"
enable_https                = true
upload_cors_allowed_origins = ["https://dev.moimyeon.plady.io", "http://localhost:3000", "http://localhost:5173"]

vpc_cidr    = "10.20.0.0/16"
db_name     = "moimyeondev"
db_username = "moimyeon_admin"
# Existing dev uses the same externally managed DB identity for app and admin.
db_master_username = null

# Public OAuth identifier. The corresponding client secret exists only in SSM.
oauth_google_client_id = "662774804169-9r00i1iuh80lmctu72144qbl1kqqqk0c.apps.googleusercontent.com"

notification_worker_desired_count     = 1
firebase_project_id                   = "moimyeon-development"
notification_web_push_action_base_url = "https://dev.moimyeon.plady.io"
notification_email_ses_from_address   = "no-reply@moimyeon.plady.io"
notification_email_gmail_address      = "100dodukteam@gmail.com"
