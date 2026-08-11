# moimyeon AWS Terraform

Terraform for the moimyeon backend on AWS, using the **ECS-on-EC2 (Pattern A)**
deployment model. Structure mirrors `100Thieves-team/plady`'s `infra/terraform`.

## Architecture

- `envs/shared`: account-level shared resources (GitHub Actions OIDC provider,
  optional Route 53 zone/domain). Account **781897847312 already has an OIDC
  provider**, so `create_oidc_provider = false` by default — shared references it.
- `envs/dev`: development environment for the `dev` branch.
- `envs/live`: production environment for the `main` branch (provisioned scaled-to-zero).
- `modules/shared-foundation`: reusable shared resources.
- `modules/moimyeon-environment`: reusable environment stack.

Each app environment creates:

- VPC with public ALB subnets, private ECS subnets, private RDS subnets, one NAT
  Gateway, and an S3 Gateway VPC endpoint.
- **RDS MySQL** on `db.t4g.micro` by default (moimyeon uses MySQL 8.4).
- **ECS on EC2** using a `t3.small` launch template, Auto Scaling Group, and ECS
  capacity provider (awsvpc tasks, deployment circuit breaker with rollback).
- ALB target group (`ip`) + HTTP/HTTPS listeners, health check `/actuator/health/readiness`.
- ACM DNS-validated certificate when `app_domain_name` is set.
- Route 53 alias when `dns_management = "route53"`, or manual CNAME outputs when
  `dns_management = "external"` (moimyeon DNS is in Cloudflare → external).
- S3 private upload bucket for the MOI-361 presigned-URL flow.
- ECR repository for backend Docker images.
- SSM parameters for generated DB password / JWT secret / Google OAuth secret and
  the last deployed image URI.
- IAM: ECS task role (S3 uploads + ECS Exec), task execution role (SSM secrets),
  ECS instance role, and a GitHub Actions deploy role restricted to the env branch.
- Optional SSM DB-access bastion (developer RDS port-forward).

## How this maps to the current (hand-built) dev infra

The live dev resources today were created outside Terraform and run the app as a
manual `docker run` on a single EC2 (see `docs/moimyeon-backend/infra/`). This
Terraform expresses the **Pattern A target** (ECS), not a 1:1 import of that
box. Reused shapes: VPC/subnet CIDRs (`10.20.0.0/16`, same offsets), ALB, RDS
MySQL, ECR, ACM, bastion. Replaced: the manual EC2 `docker run` → ECS service.

**It is authored but not yet applied.** Cutover options:
1. Fresh apply into a new VPC, then move the ALB DNS/records over (blue/green), or
2. `terraform import` the existing VPC/ALB/RDS/etc. before apply to adopt them.

## Bootstrap Order

```bash
# 1) Local AWS credentials for account 781897847312
aws sts get-caller-identity

# 2) Create the S3 state bucket + DynamoDB lock table + backend.tf files
./infra/terraform/scripts/bootstrap-s3-backend.sh

# 3) (Optional) shared — only needed for Route 53. OIDC already exists.
cd infra/terraform/envs/shared && terraform init && terraform apply

# 4) dev
cd ../dev
cp terraform.tfvars.example terraform.tfvars   # fill OAuth id/secret
terraform init && terraform plan && terraform apply
terraform output
```

On the very first apply, ECS cannot start a task until the first image is pushed
to ECR. After the first `dev` push, the deploy workflow registers a task
definition revision with the built image and updates the service.

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
| `MOIMYEON_IMAGE_URI_PARAMETER_{ENV}` | `image_uri_parameter_name` |

Push behavior (once the workflow exists): `dev` push → dev, `main` push → live.

## App config contract (injected into the ECS task)

Non-secret env: `SPRING_PROFILES_ACTIVE`, `SERVER_PORT`,
`STORAGE_DATABASE_CORE_DB_URL` (host:port/db), `STORAGE_DATABASE_CORE_DB_USERNAME`,
`GOOGLE_OAUTH_CLIENT_ID`, `AWS_REGION`.

Secrets via SSM SecureString `valueFrom` (hybrid model). All three are required —
the app will not boot without them:

| Secret | Source | Rotates on apply? |
| --- | --- | --- |
| `STORAGE_DATABASE_CORE_DB_PASSWORD` | **pre-existing SSM** (`generate_db_password = false`) — dev references it by ARN; the RDS master password is left untouched | No (preserved) |
| `JWT_SECRET` | Terraform-generated `random_password` → SSM | Yes (dev only; invalidates sessions) |
| `GOOGLE_OAUTH_CLIENT_SECRET` | tfvars → SSM | n/a |

**Before applying dev**, seed the DB password parameter with the value currently
in the box's `app.env` (this is the one manual secret step of the absorb):

```bash
aws ssm put-parameter --region ap-northeast-2 \
  --name /moimyeon/dev/core-api/DB_PASSWORD --type SecureString \
  --value "<current app.env STORAGE_DATABASE_CORE_DB_PASSWORD>"
```

For a fresh env (e.g. live) set `generate_db_password = true` (default) and
Terraform creates the parameter and the RDS master password itself.

## Open items / assumptions to confirm

- **S3 upload env keys (MOI-361):** the `storage.object-storage.s3.*` module is not
  yet on `dev`, so the app-facing upload env vars are left to
  `additional_environment` (bucket + IAM are provisioned). Wire the exact keys
  (e.g. `STORAGE_OBJECTSTORAGE_S3_BUCKET`) once MOI-361 merges.
- **Health check:** rollout is gated by the ALB target group (`/actuator/health/readiness`). The core-api readiness group checks the DB but excludes notification Redis, so a relay dependency failure does not evict an otherwise serviceable API task.
  Container-level HEALTHCHECK is off by default because the `eclipse-temurin` JRE
  image has no `wget`/`curl` — set `enable_container_health_check = true` only if
  you add one to the image.
- **DB name/username** default to `moimyeondev` / `moimyeon` — confirm against the
  existing dev RDS master user before adopting/importing it.
- **Schema:** moimyeon has no Flyway; `dev`/`live` use `ddl-auto: validate`, so the
  schema must still be applied out-of-band (`schema.sql`).

## Terraform State Backend

`scripts/bootstrap-s3-backend.sh` creates:
- S3 bucket `moimyeon-terraform-state-<account-id>-<region>` (versioned, AES256, BPA).
- DynamoDB lock table `moimyeon-terraform-locks`.
- `backend.tf` in each env (git-ignored; `backend.tf.example` is tracked).
