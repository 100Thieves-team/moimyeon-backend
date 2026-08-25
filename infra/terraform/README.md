# moimyeon AWS Terraform

Terraform for the moimyeon backend on AWS, using the **ECS-on-EC2 (Pattern A)**
deployment model. Structure mirrors `100Thieves-team/plady`'s `infra/terraform`.

## Architecture

- `envs/shared`: account-level shared resources (GitHub Actions OIDC provider,
  optional Route 53 zone/domain). Account **781897847312 already has an OIDC
  provider**, so `create_oidc_provider = false` by default — shared references it.
- `envs/dev`: development environment for the `dev` branch.
- `envs/live`: production configuration for the `main` branch. Remote state is
  not created yet; the current plan is a full scaled-to-zero environment bootstrap.
- `modules/shared-foundation`: reusable shared resources.
- `modules/moimyeon-environment`: reusable environment stack.

Each app environment creates:

- VPC with public ALB subnets, private ECS subnets, private RDS subnets, one NAT
  Gateway, and an S3 Gateway VPC endpoint.
- **RDS MySQL** on `db.t4g.micro` by default (moimyeon uses MySQL 8.4).
- Optional private **Redis ECS service** for notification Streams and relay coordination. It uses Cloud Map for private DNS and EFS for AOF persistence across task replacement.
- **ECS on EC2** using a `t3.small` launch template, Auto Scaling Group, and ECS
  capacity provider (awsvpc tasks, deployment circuit breaker with rollback).
- Independent `core-worker` ECS service without an ALB. It has its own task Security Group, execution role, runtime role, ECR repository, and CloudWatch log group.
- ALB target group (`ip`) + HTTP/HTTPS listeners, health check `/actuator/health/readiness`.
- Dedicated SSE-S3 ALB access-log bucket with 90-day retention.
- Regional AWS WAF web ACL: per-IP five-minute rate limit blocks, while Common,
  Known Bad Inputs, and IP Reputation managed groups start in Count mode. WAF
  request logs go to CloudWatch Logs with authorization and cookie headers redacted.
- ACM DNS-validated certificate when `app_domain_name` is set.
- Route 53 alias when `dns_management = "route53"`, or manual CNAME outputs when
  `dns_management = "external"` (moimyeon DNS is in Cloudflare → external).
- S3 private upload bucket for the MOI-361 presigned-URL flow.
- Separate ECR repositories for core-api and core-worker Docker images.
- SSM references for pre-created JWT, Google OAuth, Redis, and vendor
  SecureStrings, plus last-deployed image metadata. New live RDS credentials are
  generated and rotated by RDS in Secrets Manager; secret values do not enter
  Terraform configuration or new state.
- IAM: ECS task role (S3 uploads + ECS Exec), task execution role (SSM secrets),
  ECS instance role, and a GitHub Actions deploy role restricted to the env branch.
- Optional SSM DB-access bastion (developer RDS port-forward).

## Current dev infra

As of 2026-08-14, dev Core API, Notification Worker, and notification Redis run
on the Terraform-managed ECS stack. The configuration still contains explicit
blue/green compatibility settings for resources retained from the former
hand-built environment; follow the comments in `envs/dev/main.tf` when removing
those transitional dependencies.

## Bootstrap Order

```bash
# 1) Local AWS credentials for account 781897847312
aws sts get-caller-identity

# 2) Create the S3 state bucket + DynamoDB lock table + backend.tf files
./infra/terraform/scripts/bootstrap-s3-backend.sh

# 3) (Optional) shared — only needed for Route 53. OIDC already exists.
cd infra/terraform/envs/shared && terraform init && terraform apply

# 4) Validate the committed, non-secret environment sources.
cd ../../..
bash infra/terraform/tests/terraform-config-contract.sh
bash infra/terraform/scripts/terraform-command.sh validate shared
bash infra/terraform/scripts/terraform-command.sh validate dev
bash infra/terraform/scripts/terraform-command.sh validate live
```

The authoritative non-secret settings are committed as `shared.tfvars`,
`dev.tfvars`, and `live.tfvars`. Official commands always pass the matching file
with `-var-file`; `terraform.tfvars` and `*.auto.tfvars` are forbidden. A local
`local.override.tfvars` may be passed only to an explicitly ad-hoc local command
and is never accepted by CI plan or apply.

Before an environment's first apply, create the following SSM SecureStrings
through the approved operator process. Do not put their values in tfvars, shell
history, plan, or GitHub workflow inputs. Terraform constructs the ARN without
reading or owning the value.

- `/moimyeon/{env}/core-api/JWT_SECRET`
- `/moimyeon/{env}/core-api/GOOGLE_OAUTH_CLIENT_SECRET`
- `/moimyeon/{env}/core-api/DB_PASSWORD` (least-privilege application DB user)
- `/moimyeon/{env}/core-worker/FIREBASE_SERVICE_ACCOUNT_JSON`
- `/moimyeon/{env}/core-worker/NOTIFICATION_EMAIL_GMAIL_APP_PASSWORD`

On the very first apply, ECS cannot start a task until the first image is pushed
to ECR. After the first successful `dev` CI run, the deploy workflow registers a
task definition revision with the built image and updates the service.

## GitHub Repository Variables

After `apply`, sync Terraform outputs into the repo variables the deploy workflow
will consume (workflow itself is the next step — Pattern A CD):

```bash
./infra/terraform/scripts/sync-github-variables.sh --env dev        # --dry-run to preview
```

| Variable | Source (env output) |
| --- | --- |
| `MOIMYEON_AWS_REGION_{ENV}` | `aws_region` |
| `MOIMYEON_AWS_ROLE_TO_ASSUME_{ENV}` | `github_deploy_role_arn` |
| `MOIMYEON_ECR_REPOSITORY_URL_{ENV}` | `ecr_repository_url` |
| `MOIMYEON_ECS_CLUSTER_{ENV}` | `ecs_cluster_name` |
| `MOIMYEON_ECS_SERVICE_{ENV}` | `ecs_service_name` |
| `MOIMYEON_ECS_CONTAINER_NAME_{ENV}` | `ecs_container_name` |
| `MOIMYEON_ECS_TASK_DEFINITION_{ENV}` | `ecs_task_definition_arn` |
| `MOIMYEON_IMAGE_URI_PARAMETER_{ENV}` | `image_uri_parameter_name` |
| `MOIMYEON_APP_URL_{ENV}` | `app_url` |
| `MOIMYEON_DEPLOYMENT_BUNDLE_PARAMETER_PREFIX_{ENV}` | `deployment_bundle_parameter_prefix` |
| `MOIMYEON_WORKER_ECR_REPOSITORY_URL_{ENV}` | `notification_worker_ecr_repository_url` |
| `MOIMYEON_WORKER_ECS_SERVICE_{ENV}` | `notification_worker_ecs_service_name` |
| `MOIMYEON_WORKER_ECS_CONTAINER_NAME_{ENV}` | `notification_worker_ecs_container_name` |
| `MOIMYEON_WORKER_ECS_TASK_DEFINITION_{ENV}` | `notification_worker_task_definition_arn` |
| `MOIMYEON_WORKER_IMAGE_URI_PARAMETER_{ENV}` | `notification_worker_image_uri_parameter_name` |

Push behavior (once the workflow exists): a successful CI run for `dev` deploys
to dev. Documentation-only revisions do not enter the deployment queue. `main`
does not deploy until `MOIMYEON_LIVE_DEPLOY_ENABLED=true`: the promotion workflow
is fail-closed until native ECS blue/green and the promotion IAM role have been
planned and applied by a person.

Before the five Worker variables are synced, the workflow keeps deploying only
Core API. Once they are all present, it builds the Worker image while the API
stabilizes, then registers and deploys the Worker only after both sides succeed.
A Worker service with desired count `0` receives the new task definition without
starting a task, so vendor credentials can be prepared before activation.

## Release promotion and rollback

- `.github/workflows/promote-live.yml` wakes after a successful main CI run. It
  accepts only a two-parent main merge and verifies that the dev parent passed CI.
  It selects that revision's deployment bundle, or the latest first-parent bundle
  with an identical runtime tree when only documentation changed, then copies API
  and Worker manifests into live ECR without building them again.
- `.github/workflows/rollback-aws.yml` is the execution boundary used by the team
  development platform. The UI selects a prior deployment bundle and dispatches
  the full source SHA, target environment, scope, and audit reason. The browser
  never receives AWS credentials. The workflow resolves image digests and exact
  historical task-definition ARNs from the immutable SSM deployment ledger.
- Both workflows deploy immutable `repo@sha256:...` references. SSM `last deployed`
  is updated only after ECS stability and, for Core API, blocking smoke tests.
  Only then is `deployed-{env}-{sha12}` added and a single immutable SSM bundle
  manifest binds source SHA, API/Worker digests, and exact task definitions.
  Promotion and rollback require the ledger and marker to agree.
- ECR lifecycle expires only untagged images after seven days. Tagged build
  candidates and `deployed-{env}-{sha12}` markers are retained because ECR
  lifecycle rules cannot exclude a deployed image that also has a candidate tag.
  Deployment roles intentionally lack `ecr:BatchDeleteImage`, so they cannot
  delete and recreate immutable deployment markers.
- Live promotion also refuses to start until the API reports native ECS
  `BLUE_GREEN` with its alternate target group/listener infrastructure, and every
  configured live service has desired count above zero. Verify capacity before
  enabling `MOIMYEON_LIVE_DEPLOY_ENABLED`.
- `live-app` and `dev-app` GitHub Environments scope OIDC and variables but have no
  required reviewers. The person who merges main owns the resulting app deployment.
  Terraform apply remains a separate `live-infra` confirmation boundary.

Fail-closed repository variables, created only after the IAM/Terraform slice is ready:

| Variable | Purpose |
| --- | --- |
| `MOIMYEON_LIVE_DEPLOY_ENABLED` | Enables automatic main-to-live promotion only when exactly `true` |
| `MOIMYEON_ROLLBACK_ENABLED` | Enables development-platform and break-glass rollback only when exactly `true` |
| `MOIMYEON_DEPLOYMENT_LEDGER_ENABLED` | Enables immutable SSM bundle recording after IAM/prefix apply |
| `MOIMYEON_AWS_PROMOTE_ROLE_TO_ASSUME_LIVE` | OIDC role that reads deployed dev images and writes/deploys live |
| `MOIMYEON_AWS_ROLLBACK_ROLE_TO_ASSUME_DEV` | OIDC rollback role for dev bundles |
| `MOIMYEON_AWS_ROLLBACK_ROLE_TO_ASSUME_LIVE` | OIDC rollback role for live bundles |
| `MOIMYEON_DEVELOPMENT_PLATFORM_ACTOR` | Exact GitHub App/bot actor allowed to dispatch normal rollback |
| `MOIMYEON_ROLLBACK_BREAK_GLASS_ACTORS` | Comma-separated human actors allowed for audited break-glass dispatch |

GitHub secrets hold only the AWS-external Slack webhook URLs:

- `SLACK_DEPLOY_WEBHOOK_URL_DEV`
- `SLACK_DEPLOY_WEBHOOK_URL_LIVE`

Missing Slack webhooks skip notification without changing deployment success. A
configured webhook failure produces a workflow warning because the notification
steps use `continue-on-error`.

No GitHub Actions secret is added for a frontend domain change. Deployment uses
the existing `MOIMYEON_*_DEV` repository variables, while application secrets
remain in SSM.

## Dev preview frontend

The dev backend is paired only with `https://dev.moimyeon.plady.io`:

- Core API `dev` profile: OAuth completion redirects, credentialed CORS, and the
  `dev.moimyeon.plady.io` cookie domain with dev-only cookie names.
- Upload bucket: browser CORS for the preview origin and local frontend development.
- Notification Worker: FCM click actions use the preview origin.

Do not deploy this pairing until all three checks pass. Otherwise the current
production frontend, which may still call the dev API, loses login access:

```bash
dig +short dev.moimyeon.plady.io
curl -sS -o /dev/null -D - https://api.dev.moimyeon.plady.io/oauth2/authorization/google \
  | grep -iE '^(HTTP/|location:)'
```

The first command must resolve to Vercel. The OAuth response must redirect to
Google with `https://api.dev.moimyeon.plady.io/login/oauth2/code/google` as its
encoded callback. In the browser network panel, verify that preview login starts
at this dev API and that production login does not target the dev API.
In Vercel, assign the custom domain to a long-lived preview branch; assigning it
only to one deployment will not advance it on later Git pushes.

Change the reviewed non-secret values in `envs/dev/dev.tfvars` by PR:

```hcl
upload_cors_allowed_origins             = ["https://dev.moimyeon.plady.io", "http://localhost:3000", "http://localhost:5173"]
notification_web_push_action_base_url   = "https://dev.moimyeon.plady.io"
```

CI creates the plan from the committed file. After merge it recreates an exact
merged-SHA plan, waits behind the `dev-infra` Environment, verifies the checksum,
and applies that binary plan. Do not run a local apply:

```bash
git add infra/terraform/envs/dev/dev.tfvars
git commit
# Open and merge a PR after Terraform Plan passes.
```

Changing the frontend hostname does not rotate Firebase or Gmail credentials.
Keep the dev-only Firebase project/service account and Gmail app password in the
existing SSM paths, and verify their metadata without decrypting them:

```bash
aws ssm describe-parameters --profile plady --region ap-northeast-2 \
  --parameter-filters 'Key=Name,Option=BeginsWith,Values=/moimyeon/dev/core-worker/' \
  --query 'sort_by(Parameters,&Name)[].{Name:Name,Type:Type,Version:Version}' \
  --output table
```

## App config contract (injected into the ECS task)

Non-secret env: `SPRING_PROFILES_ACTIVE`, `SERVER_PORT`,
`STORAGE_DATABASE_CORE_DB_URL` (host:port/db), `STORAGE_DATABASE_CORE_DB_USERNAME`,
`GOOGLE_OAUTH_CLIENT_ID`, `AWS_REGION`.

Secrets are injected with ECS `valueFrom`. `STORAGE_REDIS_URL` is required when
notification Redis is enabled:

| Secret | Source | Rotates on apply? |
| --- | --- | --- |
| `STORAGE_DATABASE_CORE_DB_PASSWORD` | Pre-created SSM SecureString for the least-privilege application DB user | Manual, coordinated with DB user rotation |
| `JWT_SECRET` | Pre-created SSM SecureString → `/moimyeon/{env}/core-api/JWT_SECRET` | Manual; rotation invalidates sessions |
| `GOOGLE_OAUTH_CLIENT_SECRET` | Pre-created SSM SecureString → `/moimyeon/{env}/core-api/GOOGLE_OAUTH_CLIENT_SECRET` | Manual |
| `STORAGE_REDIS_URL` | Pre-created private Redis URL → `/moimyeon/{env}/shared/STORAGE_REDIS_URL` | Manual |

Before enabling Redis, create its password and URL without passing either value
through Terraform:

```bash
REDIS_PASSWORD="$(openssl rand -hex 32)"

aws ssm put-parameter --region ap-northeast-2 \
  --name /moimyeon/dev/notification-redis/PASSWORD \
  --type SecureString --value "${REDIS_PASSWORD}" --overwrite

aws ssm put-parameter --region ap-northeast-2 \
  --name /moimyeon/dev/shared/STORAGE_REDIS_URL \
  --type SecureString \
  --value "redis://:${REDIS_PASSWORD}@notification-redis.moimyeon-dev.internal:6379" \
  --overwrite

unset REDIS_PASSWORD
```

The worker additionally expects two pre-created SecureStrings. Terraform references their ARNs without reading the secret values into state:

| Worker secret | SSM parameter |
| --- | --- |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | `/moimyeon/{env}/core-worker/FIREBASE_SERVICE_ACCOUNT_JSON` |
| `NOTIFICATION_EMAIL_GMAIL_APP_PASSWORD` | `/moimyeon/{env}/core-worker/NOTIFICATION_EMAIL_GMAIL_APP_PASSWORD` |

Keep `notification_worker_desired_count = 0` until those parameters and the
non-secret FCM/email values in the committed environment tfvars are ready. The
worker Task Role receives `ses:SendEmail` only when a sender address is configured,
and the API Task Role does not receive that permission.

**Before applying dev**, seed the DB password parameter with the value currently
in the box's `app.env` (this is the one manual secret step of the absorb):

```bash
aws ssm put-parameter --region ap-northeast-2 \
  --name /moimyeon/dev/core-api/DB_PASSWORD --type SecureString \
  --value "<current app.env STORAGE_DATABASE_CORE_DB_PASSWORD>"
```

For live, `manage_db_master_password = true` lets RDS generate and rotate the
master password in Secrets Manager. Terraform state contains the secret ARN, not
the password value. ECS cannot read that admin secret. After the live RDS bootstrap,
an operator uses the admin credential once to create the committed `db_username`
with least-privilege grants and the password already stored in SSM `DB_PASSWORD`.
Only then may API or Worker capacity be raised. RDS master rotation therefore
does not invalidate long-running ECS task environment variables.

## Open items / assumptions to confirm

- **S3 upload env keys (MOI-361):** the `storage.object-storage.s3.*` module is not
  yet on `dev`, so the app-facing upload env vars are left to
  `additional_environment` (bucket + IAM are provisioned). Wire the exact keys
  (e.g. `STORAGE_OBJECTSTORAGE_S3_BUCKET`) once MOI-361 merges.
- **Health check:** rollout is gated by the ALB target group (`/actuator/health/readiness`). The core-api readiness group checks the DB but excludes notification Redis, so a relay dependency failure does not evict an otherwise serviceable API task.
  Container-level HEALTHCHECK is off by default because the `eclipse-temurin` JRE
  image has no `wget`/`curl` — set `enable_container_health_check = true` only if
  you add one to the image.
- **Notification Redis:** dev runs one pinned Redis container on the existing ECS capacity provider. AOF uses `appendfsync always` and `/data` is mounted from encrypted EFS, so a task or EC2 replacement can recover the persisted Stream. Cloud Map provides the stable private DNS name. `appendfsync always` intentionally trades write throughput for the relay contract: the DB Outbox is deleted after `XADD` returns, so `everysec` would reopen an acknowledged-message loss window. Benchmark this before live; changing it safely requires changing the relay durability protocol too. This is still a single Redis process: ECS restarts it after failure, but there is a temporary outage and no automatic replica promotion. Live remains disabled while ECS capacity is zero; high availability requires a separate replication/Sentinel slice before live activation.
- **DB name/username** default to `moimyeondev` / `moimyeon` — confirm against the
  existing dev RDS master user before adopting/importing it.
- **Schema:** `core-api` applies `storage:db-core` Flyway migrations in `dev`,
  `staging`, and `live`; `core-worker` keeps Flyway disabled. Persistent databases
  must not receive `schema.sql` directly.

## Terraform State Backend

`scripts/bootstrap-s3-backend.sh` creates:
- S3 bucket `moimyeon-terraform-state-<account-id>-<region>` (versioned, AES256, BPA).
- DynamoDB lock table `moimyeon-terraform-locks`.
- `backend.tf` in each env (git-ignored; `backend.tf.example` is tracked).

`backend.tf.example` is the committed, credentials-free backend source. The
official command refuses a generated `backend.tf` that differs from it. AWS
credentials are supplied only by the local bootstrap identity or GitHub OIDC.

## Terraform CI bootstrap

`.github/workflows/terraform-plan.yml` runs fork-safe fmt/validate, internal PR
plans, and daily drift plans. After the merged revision passes `CI`,
`.github/workflows/terraform-apply.yml` pins each run to its CI-successful trigger
revision and creates a plan even when that commit is app-only. It
applies only roots with resource or output changes. This avoids losing an older
Terraform change when a newer app/docs CI finishes first.

The `shared-foundation` module creates the bootstrap boundary itself:

- rotating KMS key and private, versioned, Block-Public-Access S3 plan bucket
- enforced KMS headers and `If-None-Match` create-only writes at the bucket policy
- 24-hour current/noncurrent plan expiry and deletion denial for CI principals
- separate `terraform-review-plan`, `terraform-drift-plan`, and
  `terraform-apply-plan` OIDC roles/prefixes with a repository-owned
  metadata-only refresh allowlist (no broad AWS `ReadOnlyAccess`)
- separate `shared-infra`, `dev-infra`, and `live-infra` exact-plan apply roles

The conditional-write policy follows the
[AWS S3 enforced conditional writes contract](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes-enforce.html).
Configure these non-secret GitHub variables before enabling Terraform CI:

- `MOIMYEON_TERRAFORM_CI_ENABLED=false` during bootstrap; set exactly `true` only after the following preflight
- `MOIMYEON_TERRAFORM_PLAN_ROLE_TO_ASSUME` in `terraform-review-plan`; it may
  write only create-only `plans/pr/*` objects
- `MOIMYEON_TERRAFORM_PLAN_ROLE_TO_ASSUME` in `terraform-drift-plan`; it may
  write only create-only `plans/drift/*` objects
- `MOIMYEON_TERRAFORM_APPLY_PLAN_ROLE_TO_ASSUME` for trusted dev/main push
  planning in `terraform-apply-plan`; it may write only create-only
  `plans/apply/*` objects
- `MOIMYEON_TERRAFORM_APPLY_ROLE_TO_ASSUME` separately in `shared-infra`,
  `dev-infra`, and `live-infra`
- `MOIMYEON_TERRAFORM_PLAN_BUCKET`
- `MOIMYEON_TERRAFORM_PLAN_KMS_KEY_ARN`
- `MOIMYEON_TERRAFORM_VARIABLE_SYNC_TOKEN` secret separately in `dev-infra`
  and `live-infra`; use a short-lived fine-grained token with repository
  Variables write and Metadata read only (prefer a GitHub App installation token
  when the team platform can mint one)

While `MOIMYEON_TERRAFORM_CI_ENABLED` is not exactly `true`, the Terraform
boundary deliberately fails and application deploy/promotion remains frozen.
This prevents a commit containing infrastructure and application changes from
running the application on old infrastructure during bootstrap.

The three plan roles need read access for Terraform refresh plus write access only
to their disjoint plan-artifact prefixes. Neither writer may delete or overwrite
objects, and the bucket policy denies non-conditional and cross-prefix writes. Apply roles need the matching state/backend and
environment resource permissions. The artifact bucket must block public access,
use the configured KMS key, enable versioning, and expire plan objects within 24
hours. Raw plans and `terraform show -json` are never GitHub artifacts or PR text.

`terraform-review-plan` requires a human reviewer with self-review prevention
because an internal PR controls Terraform configuration and scripts. It must not
read remote state or receive an OIDC token until the reviewer approves that exact
diff. `terraform-drift-plan` accepts only the default branch schedule without a
review pause. `terraform-apply-plan` accepts only trusted `dev`/`main` branches and uses a
different OIDC subject and role. `shared-infra`, `dev-infra`, and `live-infra`
require human apply review. This does not reintroduce reviewers for `dev-app` or
`live-app`. Apply the one-time dev JWT/OAuth state-forget plan before turning
`MOIMYEON_TERRAFORM_CI_ENABLED` on, so the plan role never starts with current
state objects that still contain those secret values.

Plan roles may refresh resource and bucket metadata, read only the three exact
backend state objects, and read the public ECS optimized AMI plus non-secret
Core API/Worker `IMAGE_URI` parameters. They cannot read application S3 objects,
SSM SecureString paths, Secrets Manager values, KMS plaintext, or their own raw
plan artifact. This is a repository-owned policy so an AWS managed policy update
cannot silently add a new data-plane read.

Apply roles deliberately retain broad environment infrastructure permissions:
`PowerUserAccess` plus project-prefixed IAM role management. The current trust
boundary is the exact merged source, sanitized binary-plan summary, and human
approval on each `*-infra` job. A malicious approved workflow could still use
project roles to expand permissions because a permissions boundary is not yet
enforced. Add an apply-role permissions boundary and `iam:PolicyARN` attachment
allowlist as a hardening follow-up after the bootstrap path is proven.

GitHub evaluates Environment deployment branches against the workflow execution
ref. `workflow_run` and its reusable calls execute from the default branch
(`dev`) even when the pinned source SHA belongs to `main`, so
`terraform-apply-plan` and `live-infra` allow the `dev` execution ref. The
workflow's source-branch/SHA verification remains the authority for main.

### One-time bootstrap handoff

The first apply is intentionally circular: Terraform CI needs the OIDC roles and
plan store that Terraform creates. Close the GitHub Environment subjects first;
this prevents an internal PR from receiving an unreviewed Environment OIDC token
after the AWS roles appear:

```bash
infra/terraform/scripts/sync-terraform-bootstrap.sh \
  --phase environments --reviewer GITHUB_LOGIN
infra/terraform/scripts/sync-terraform-bootstrap.sh \
  --phase environments --reviewer GITHUB_LOGIN --apply
```

Then generate and review the exact shared plan and use an existing human AWS
identity for this single apply:

```bash
AWS_PROFILE=plady aws sts get-caller-identity --query Account --output text
AWS_PROFILE=plady bash infra/terraform/scripts/terraform-command.sh plan \
  shared /tmp/moimyeon-shared-bootstrap.tfplan
AWS_PROFILE=plady terraform -chdir=infra/terraform/envs/shared apply \
  /tmp/moimyeon-shared-bootstrap.tfplan
```

The identity check must print the reviewed account `781897847312`. Do not apply
when the account differs.

The repository agent must stop after plan; the apply command is an operator
action. After apply, preview and reconcile non-secret Variables:

```bash
AWS_PROFILE=plady infra/terraform/scripts/sync-terraform-bootstrap.sh --phase variables
AWS_PROFILE=plady infra/terraform/scripts/sync-terraform-bootstrap.sh --phase variables --apply
```

The script creates/reconciles the six Terraform Environments, branch policies,
and role/bucket/KMS Variables while deliberately keeping
`MOIMYEON_TERRAFORM_CI_ENABLED=false`. It never handles the Variables-write
secret. Add `MOIMYEON_TERRAFORM_VARIABLE_SYNC_TOKEN` to `dev-infra` and
`live-infra`, verify a review plan and drift plan, and only then enable the gate.

On dev, shared is planned and applied first; only then is the dev plan created,
so an exact dev plan cannot capture pre-shared state. Each apply job acquires the
environment mutation lock and then resolves the latest CI-successful `dev` or
`main` first-parent revision. A
plan whose trigger is no longer the latest CI-successful revision fails before AWS apply
credentials are configured,
preventing out-of-order workflow completion from rolling infrastructure back to
an older commit.

Dev/live Terraform apply shares `deploy-aws-{env}` concurrency with application
deploy, promotion, and rollback. Shared uses `terraform-shared`. Terraform and
application workflows therefore cannot mutate the same ECS/ECR/IAM boundary at
the same time.

Application deploy and live promotion also wait for the `Terraform Apply
{branch}@{CI SHA}` workflow run to succeed before entering their AWS mutation
jobs. The shared lock prevents overlap; this explicit workflow dependency ensures
Terraform completes first when one commit changes both infrastructure and app.
If only documentation commits become CI-successful after a Terraform-ready app
candidate, the mutation job permits that runtime-equivalent candidate; any newer
runtime or infrastructure change requires its own successful Terraform boundary.

Repository security must also enable GitHub secret scanning and push protection.
CI runs the pinned Gitleaks image over full history; `.gitleaksignore` contains
only exact historical test-fixture fingerprints, and `.gitleaks.toml` allows only
deterministic test placeholders rather than whole files or directories.
