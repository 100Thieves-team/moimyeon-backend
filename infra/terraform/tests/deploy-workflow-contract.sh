#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
WORKFLOW="${ROOT_DIR}/.github/workflows/deploy-aws.yml"
DOCKERFILE="${ROOT_DIR}/Dockerfile"
DEPLOY_CANDIDATE_RESOLVER="${ROOT_DIR}/infra/terraform/scripts/resolve-deploy-candidate.sh"

fail() {
  echo "배포 워크플로 계약 위반: $1" >&2
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

last_line_of() {
  local file="$1"
  local pattern="$2"

  grep -nE -- "${pattern}" "${file}" | tail -n 1 | cut -d: -f1
}

assert_contains "${WORKFLOW}" '^[[:space:]]+workflow_run:' "배포는 CI workflow_run에 종속해야 한다."
assert_contains "${WORKFLOW}" 'workflows:[[:space:]]*\[[[:space:]]*CI[[:space:]]*\]' "CI 완료만 배포를 깨워야 한다."
assert_contains "${WORKFLOW}" 'github\.event\.workflow_run\.conclusion[[:space:]]*==[[:space:]]*'\''success'\''' "CI 성공 결론을 검사해야 한다."
assert_contains "${WORKFLOW}" 'github\.event\.workflow_run\.event[[:space:]]*==[[:space:]]*'\''push'\''' "PR workflow_run은 배포 권한을 얻으면 안 된다."
assert_contains "${WORKFLOW}" 'github\.event\.workflow_run\.head_repository\.full_name[[:space:]]*==[[:space:]]*github\.repository' "외부 저장소의 workflow_run은 배포 권한을 얻으면 안 된다."
assert_contains "${WORKFLOW}" 'github\.event\.workflow_run\.head_sha' "CI가 검증한 정확한 SHA를 사용해야 한다."
assert_contains "${WORKFLOW}" 'wait-for-terraform-apply\.sh' "앱 deploy는 같은 CI SHA의 Terraform boundary 성공을 기다려야 한다."
assert_contains "${WORKFLOW}" 'dev[[:space:]]+"\$\{resolved_sha\}"' "waiter는 trigger가 아니라 실제 resolved deploy SHA를 확인해야 한다."
assert_contains "${WORKFLOW}" 'Resolve Terraform-ready dev candidate' "최신 CI 성공 revision의 Terraform boundary를 lock 전에 기다려야 한다."
assert_contains "${WORKFLOW}" 'Verify Terraform-ready dev candidate freshness' "mutation lock 뒤 더 최신 revision을 Terraform보다 먼저 배포하면 안 된다."
assert_contains "${WORKFLOW}" 'documentation-only successors' "후속 docs-only CI 때문에 이미 검증된 runtime 배포를 영구 누락하면 안 된다."
assert_contains "${WORKFLOW}" 'git diff --name-only --no-renames "\$\{DEPLOY_SHA\}" "\$\{resolved_sha\}"' "candidate 이후 runtime-equivalence를 rename-safe하게 확인해야 한다."
assert_not_contains "${WORKFLOW}" '^[[:space:]]{2}push:' "배포 워크플로가 push에 독립 발화하면 안 된다."
assert_contains "${WORKFLOW}" 'branches:[[:space:]]*\[[[:space:]]*dev[[:space:]]*\]' "첫 슬라이스 자동 배포는 dev로 제한해야 한다."
assert_contains "${WORKFLOW}" 'deploy_required' "문서 전용 변경을 제외하는 gate가 있어야 한다."
assert_contains "${WORKFLOW}" '^[[:space:]]{4}concurrency:' "문서 전용 실행이 pending 배포를 대체하지 않도록 concurrency는 deploy job에 있어야 한다."
assert_contains "${WORKFLOW}" 'queue:[[:space:]]*max' "동일 환경의 pending mutation run을 새 run이 교체하면 안 된다."
assert_not_contains "${WORKFLOW}" '^concurrency:' "workflow-level concurrency는 문서 전용 실행이 pending 배포를 대체하게 만든다."
assert_contains "${WORKFLOW}" 'actions:[[:space:]]*read' "deploy 후보 resolver는 최소 actions read 권한만 가져야 한다."
assert_contains "${WORKFLOW}" 'git diff --name-only --no-renames' "코드를 docs로 옮긴 rename도 배포 변경으로 판정해야 한다."
assert_contains "${DEPLOY_CANDIDATE_RESOLVER}" 'status=success' "CI 성공 이력만 배포 후보로 사용해야 한다."
assert_contains "${DEPLOY_CANDIDATE_RESOLVER}" 'event=push' "PR CI revision은 배포 후보가 아니어야 한다."
assert_contains "${DEPLOY_CANDIDATE_RESOLVER}" 'git rev-list --first-parent' "현재 dev 계보에서 가장 최신 성공 revision을 골라야 한다."

assert_contains "${DOCKERFILE}" '^FROM .* AS core-api$' "Core API runtime target이 필요하다."
assert_contains "${DOCKERFILE}" '^FROM .* AS core-worker$' "Worker runtime target이 필요하다."
assert_contains "${DOCKERFILE}" ':core:core-api:bootJar' "공통 build가 Core API bootJar를 만들어야 한다."
assert_contains "${DOCKERFILE}" ':core:core-worker:bootJar' "공통 build가 Worker bootJar를 만들어야 한다."

assert_contains "${WORKFLOW}" 'target:[[:space:]]*core-api' "API 이미지는 core-api target을 빌드해야 한다."
assert_contains "${WORKFLOW}" 'docker buildx build.*--target core-worker' "Worker 이미지는 core-worker target을 빌드해야 한다."
assert_contains "${WORKFLOW}" 'timeout --signal=TERM 900 docker buildx build' "Worker 빌드는 API 커밋을 무기한 막지 않도록 시간 상한이 있어야 한다."
assert_contains "${WORKFLOW}" 'worker_build_succeeded' "Worker 빌드 실패를 Worker 배포 gate로 전달해야 한다."
assert_contains "${WORKFLOW}" 'Reusing existing immutable Core API image' "immutable ECR tag가 있으면 동일 SHA 재빌드를 건너뛰어야 한다."
assert_contains "${WORKFLOW}" 'Reusing existing immutable Worker image' "Worker immutable tag가 있으면 동일 SHA 재빌드를 건너뛰어야 한다."

api_update_line="$(line_of "${WORKFLOW}" 'aws ecs update-service')"
worker_build_line="$(line_of "${WORKFLOW}" 'docker buildx build.*--target core-worker')"
api_wait_line="$(line_of "${WORKFLOW}" 'deadline=\$\(\(SECONDS \+ 1500\)\)')"
api_ssm_commit_line="$(line_of "${WORKFLOW}" 'Commit the API deployment boundary')"
worker_wait_line="$(last_line_of "${WORKFLOW}" 'deadline=\$\(\(SECONDS \+ 1500\)\)')"
worker_ssm_commit_line="$(line_of "${WORKFLOW}" 'name: Store Notification Worker image URI in SSM')"

if [ "${api_update_line}" -ge "${worker_build_line}" ]; then
  fail "Worker 빌드는 API 배포 시작 뒤에 실행해야 한다."
fi

if [ "${worker_build_line}" -ge "${api_wait_line}" ]; then
  fail "Worker 빌드는 API 안정화 대기 전에 시작해야 한다."
fi

if [ "${api_ssm_commit_line}" -le "${api_wait_line}" ]; then
  fail "API 이미지 SSM은 API 안정화가 끝난 뒤에만 갱신해야 한다."
fi

if [ "${worker_ssm_commit_line}" -le "${worker_wait_line}" ]; then
  fail "Worker 이미지 SSM은 Worker 안정화가 끝난 뒤에만 갱신해야 한다."
fi

echo "배포 워크플로 계약을 만족한다."
