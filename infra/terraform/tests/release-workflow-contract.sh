#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
DEV_WORKFLOW="${ROOT_DIR}/.github/workflows/deploy-aws.yml"
PROMOTE_WORKFLOW="${ROOT_DIR}/.github/workflows/promote-live.yml"
ROLLBACK_WORKFLOW="${ROOT_DIR}/.github/workflows/rollback-aws.yml"
DEPLOY_SCRIPT="${ROOT_DIR}/infra/terraform/scripts/deploy-ecs-image.sh"
COPY_SCRIPT="${ROOT_DIR}/infra/terraform/scripts/copy-image-by-digest.sh"
SMOKE_SCRIPT="${ROOT_DIR}/infra/terraform/scripts/smoke-test.sh"
NOTIFY_SCRIPT="${ROOT_DIR}/infra/terraform/scripts/notify-deployment.sh"
RECORD_BUNDLE_SCRIPT="${ROOT_DIR}/infra/terraform/scripts/record-deployment-bundle.sh"
READ_BUNDLE_SCRIPT="${ROOT_DIR}/infra/terraform/scripts/read-deployment-bundle.sh"
ECR_MODULE="${ROOT_DIR}/infra/terraform/modules/moimyeon-environment/ecr.tf"
IAM_MODULE="${ROOT_DIR}/infra/terraform/modules/moimyeon-environment/iam.tf"

fail() {
  echo "릴리스 워크플로 계약 위반: $1" >&2
  exit 1
}

assert_contains() {
  local file="$1"
  local pattern="$2"
  local message="$3"

  grep -Eq -- "${pattern}" "${file}" || fail "${message}"
}

assert_not_contains() {
  local file="$1"
  local pattern="$2"
  local message="$3"

  if grep -Eq -- "${pattern}" "${file}"; then
    fail "${message}"
  fi
}

line_of() {
  local file="$1"
  local pattern="$2"

  grep -nEm1 -- "${pattern}" "${file}" | cut -d: -f1
}

assert_contains "${PROMOTE_WORKFLOW}" 'workflows:[[:space:]]*\[[[:space:]]*CI[[:space:]]*\]' "live 승격은 CI에 종속해야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'branches:[[:space:]]*\[[[:space:]]*main[[:space:]]*\]' "main CI만 live 승격을 깨워야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'workflow_run\.conclusion == '\''success'\''' "실패한 main CI는 승격할 수 없다."
assert_contains "${PROMOTE_WORKFLOW}" 'head_repository\.full_name == github\.repository' "외부 저장소 workflow_run은 live 권한을 얻을 수 없다."
assert_contains "${PROMOTE_WORKFLOW}" 'LIVE_DEPLOY_ENABLED:.*MOIMYEON_LIVE_DEPLOY_ENABLED' "Terraform 선행 작업 전 live 승격은 비활성 상태여야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'environment:[[:space:]]*live-app' "live-app OIDC·변수 경계를 사용해야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'copy-image-by-digest\.sh' "live는 dev image digest를 복사해야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'read-deployment-bundle\.sh' "main source는 immutable deployment ledger에서 읽어야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'wait-for-terraform-apply\.sh' "live promotion은 같은 main CI SHA의 Terraform boundary 성공을 기다려야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'main[[:space:]]+"\$\{resolved_main\}"' "promotion waiter는 trigger가 아니라 실제 resolved main SHA를 확인해야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'Verify Terraform-ready main candidate freshness' "live mutation lock 뒤 더 최신 main을 Terraform보다 먼저 승격하면 안 된다."
assert_contains "${PROMOTE_WORKFLOW}" 'documentation-only successors' "후속 docs-only main 때문에 이미 검증된 runtime 승격을 영구 누락하면 안 된다."
assert_contains "${PROMOTE_WORKFLOW}" 'git diff --name-only --no-renames "\$\{CANDIDATE_MAIN_SHA\}" "\$\{resolved_main\}"' "promotion candidate 이후 runtime-equivalence를 rename-safe하게 확인해야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'deployed-dev-\$\{deploy_sha12\}' "live 승격은 dev 배포 성공 marker를 조회해야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'deployment ledger and marker digest disagree' "live 승격은 dev ledger와 marker digest 일치를 강제해야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'git rev-list --first-parent "\$\{MAIN_SOURCE_SHA\}"' "docs-only dev source에 ledger가 없으면 runtime-equivalent 배포 bundle을 찾아야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'git diff --name-only --no-renames "\$\{revision\}" "\$\{MAIN_SOURCE_SHA\}"' "fallback bundle은 main source와 runtime 동등해야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'candidate_worker_image_uri' "fallback bundle은 live Worker 활성 형태와 일치해야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'refs/remotes/origin/main main' "deploy lock 뒤 최신 CI 성공 main revision을 다시 선택해야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'ref:.*github\.event\.workflow_run\.head_sha' "권한 job의 초기 tooling도 CI 성공 main SHA여야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'git diff --name-only --no-renames "\$\{parent_two\}" "\$\{resolved_main\}"' "main merge와 dev source의 runtime 동등성을 확인해야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'configured_worker_values' "Worker promotion 변수는 all-or-none으로 검증해야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'Restore Core API when Worker promotion fails' "Worker 실패 시 API 부분 승격을 보상해야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'Restore previous live bundle when bundle commit fails' "marker·ledger 중 하나라도 실패하면 live bundle 전체를 보상해야 한다."
assert_contains "${PROMOTE_WORKFLOW}" "steps\.record_bundle\.outcome != 'success'" "ledger 성공 전 모든 bundle commit 실패를 보상해야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'desiredCount > 0 before promotion is enabled' "scaled-to-zero live service를 성공 bundle로 기록하면 안 된다."
assert_contains "${PROMOTE_WORKFLOW}" 'require_running_service "\$\{LIVE_API_SERVICE\}" BLUE_GREEN' "live API는 native ECS blue/green apply 뒤에만 승격해야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'advancedConfiguration\.alternateTargetGroupArn' "live API blue/green load balancer 구성을 사전 검증해야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'notify-selection-failure:' "source 선택 실패도 Slack에 알려야 한다."
assert_not_contains "${PROMOTE_WORKFLOW}" 'docker/build-push-action' "live에서 image를 다시 빌드하면 안 된다."
assert_not_contains "${PROMOTE_WORKFLOW}" 'docker buildx build' "live에서 Docker build를 실행하면 안 된다."

promote_api_line="$(line_of "${PROMOTE_WORKFLOW}" 'name: Deploy Core API')"
promote_worker_line="$(line_of "${PROMOTE_WORKFLOW}" 'name: Deploy Worker')"
if [ "${promote_api_line}" -ge "${promote_worker_line}" ]; then
  fail "live Worker는 Core API 성공 뒤에 배포해야 한다."
fi

assert_contains "${ROLLBACK_WORKFLOW}" '^[[:space:]]{2}workflow_dispatch:' "개발 플랫폼이 rollback workflow를 호출할 수 있어야 한다."
assert_contains "${ROLLBACK_WORKFLOW}" 'ROLLBACK_ENABLED.*MOIMYEON_ROLLBACK_ENABLED' "전용 IAM 준비 전 rollback 요청은 명시적으로 실패해야 한다."
assert_contains "${ROLLBACK_WORKFLOW}" 'deployed-\$\{env_name\}-' "rollback은 배포 성공 marker tag만 선택해야 한다."
assert_contains "${ROLLBACK_WORKFLOW}" 'group:[[:space:]]*deploy-aws-\$\{\{ inputs\.environment \}\}' "rollback은 같은 환경 deploy와 mutation lock을 공유해야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'queue:[[:space:]]*max' "live pending mutation run을 새 run이 교체하면 안 된다."
assert_contains "${ROLLBACK_WORKFLOW}" 'queue:[[:space:]]*max' "rollback pending run을 다른 mutation이 교체하면 안 된다."
assert_contains "${ROLLBACK_WORKFLOW}" 'source_sha:' "rollback bundle은 full source SHA를 전달해야 한다."
assert_not_contains "${ROLLBACK_WORKFLOW}" 'api_task_definition_arn:' "rollback task definition을 caller 자유 입력으로 받으면 안 된다."
assert_not_contains "${ROLLBACK_WORKFLOW}" 'worker_task_definition_arn:' "rollback Worker task definition을 caller 자유 입력으로 받으면 안 된다."
assert_contains "${ROLLBACK_WORKFLOW}" 'verify-release-source\.sh' "과거 CI 성공 source만 rollback할 수 있다."
assert_contains "${ROLLBACK_WORKFLOW}" 'MOIMYEON_DEVELOPMENT_PLATFORM_ACTOR' "개발 플랫폼 또는 명시된 break-glass actor만 rollback할 수 있다."
assert_contains "${ROLLBACK_WORKFLOW}" '^  authorize:' "rollback actor·입력 검증은 mutation job 밖에서 끝나야 한다."
assert_contains "${ROLLBACK_WORKFLOW}" 'github\.ref == '\''refs/heads/dev'\''' "rollback은 default dev ref의 검증된 workflow만 실행해야 한다."
assert_contains "${ROLLBACK_WORKFLOW}" 'needs:[[:space:]]*authorize' "승인된 요청만 rollback mutation job에 들어가야 한다."
assert_contains "${ROLLBACK_WORKFLOW}" 'ref:.*needs\.authorize\.outputs\.tooling_sha' "OIDC rollback job은 authorize가 고정한 tooling SHA를 실행해야 한다."
assert_contains "${ROLLBACK_WORKFLOW}" 'existing-task-definition' "rollback은 과거 task definition 전체를 복원해야 한다."
assert_contains "${ROLLBACK_WORKFLOW}" 'read-deployment-bundle\.sh' "rollback image와 task definition은 ledger에서 함께 읽어야 한다."
assert_contains "${ROLLBACK_WORKFLOW}" 'Restore Core API when Worker rollback fails' "both rollback의 Worker 실패 시 API를 보상해야 한다."
assert_contains "${ROLLBACK_WORKFLOW}" 'deploy-ecs-image\.sh' "rollback은 공통 ECS commit 경계를 사용해야 한다."
assert_not_contains "${ROLLBACK_WORKFLOW}" 'docker/build-push-action' "rollback에서 image를 다시 빌드하면 안 된다."
assert_not_contains "${ROLLBACK_WORKFLOW}" 'docker buildx build' "rollback에서 Docker build를 실행하면 안 된다."

assert_contains "${DEV_WORKFLOW}" 'smoke-test\.sh' "dev API도 blocking smoke를 통과해야 한다."
assert_contains "${DEV_WORKFLOW}" 'deployed-dev-' "dev 안정화 뒤 deployment marker를 기록해야 한다."
assert_contains "${DEV_WORKFLOW}" 'notify-deployment\.sh' "dev 배포 결과를 Slack에 알려야 한다."
assert_contains "${PROMOTE_WORKFLOW}" 'if: always\(\)' "live 결과 알림은 실패 경로에서도 실행해야 한다."
assert_contains "${ROLLBACK_WORKFLOW}" 'if:.*always\(\)' "rollback 결과 알림은 실패 경로에서도 실행해야 한다."

smoke_line="$(line_of "${DEPLOY_SCRIPT}" 'if ! bash .*smoke-test\.sh')"
ssm_line="$(line_of "${DEPLOY_SCRIPT}" 'if ! put_ssm_with_retry "\$\{image_uri\}"')"
if [ "${smoke_line}" -ge "${ssm_line}" ]; then
  fail "smoke 성공 뒤에만 SSM last deployed를 commit해야 한다."
fi
assert_contains "${DEPLOY_SCRIPT}" 'aws ecs wait services-stable' "ECS 안정화를 기다린 뒤 commit해야 한다."
assert_contains "${DEPLOY_SCRIPT}" 'SSM commit failed; restoring previous ECS and SSM state' "SSM 실패도 이전 ECS 상태로 보상해야 한다."

assert_contains "${DEPLOY_SCRIPT}" '@sha256:\[0-9a-f\]\{64\}' "ECS에는 immutable digest만 적용해야 한다."
assert_contains "${DEPLOY_SCRIPT}" 'Smoke failed; restoring previous task definition' "smoke 실패는 이전 ECS revision을 실제 복원해야 한다."
assert_contains "${DEPLOY_SCRIPT}" 'Historical task definition belongs to a different ECS family' "rollback task definition은 같은 service family여야 한다."
assert_contains "${DEV_WORKFLOW}" 'Container .* is absent from Core API task definition' "Core API container mismatch는 marker 생성 전에 실패해야 한다."
assert_contains "${COPY_SCRIPT}" 'target_digest.*source_digest' "registry 복사 뒤 digest 동일성을 확인해야 한다."
assert_contains "${SMOKE_SCRIPT}" '/actuator/health/readiness' "readiness smoke가 필요하다."
assert_contains "${SMOKE_SCRIPT}" '/v1/terms' "공개 DB read smoke가 필요하다."
assert_contains "${NOTIFY_SCRIPT}" 'Slack webhook is not configured' "webhook 미설정은 배포 실패가 아니어야 한다."
assert_contains "${RECORD_BUNDLE_SCRIPT}" 'already exists with different immutable content' "deployment bundle ledger는 덮어쓸 수 없어야 한다."
assert_contains "${READ_BUNDLE_SCRIPT}" 'taskDefinitionArn' "ledger는 과거 task definition ARN을 검증해야 한다."
assert_contains "${ECR_MODULE}" 'image_tag_mutability[[:space:]]*=[[:space:]]*"IMMUTABLE"' "deployment marker repository는 registry 수준에서 immutable이어야 한다."
assert_contains "${ECR_MODULE}" 'tagStatus[[:space:]]*=[[:space:]]*"untagged"' "ECR lifecycle은 untagged image만 자동 만료해야 한다."
assert_not_contains "${ECR_MODULE}" 'tagStatus[[:space:]]*=[[:space:]]*"tagged"' "tagged candidate와 deployed marker를 같은 lifecycle로 만료하면 안 된다."
assert_not_contains "${IAM_MODULE}" 'ecr:BatchDeleteImage' "deployment role이 immutable marker를 삭제·재생성할 권한을 가지면 안 된다."

echo "릴리스 워크플로 계약을 만족한다."
