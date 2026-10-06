#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
WORKFLOW="${ROOT_DIR}/.github/workflows/deploy-aws.yml"
DOCKERFILE="${ROOT_DIR}/Dockerfile"
CI_WORKFLOW="${ROOT_DIR}/.github/workflows/ci.yml"
TERRAFORM_APPLY_WORKFLOW="${ROOT_DIR}/.github/workflows/terraform-apply.yml"
VERIFY_PR_CI="${ROOT_DIR}/infra/terraform/scripts/verify-pr-ci.sh"
BOUNDARY_WAITER="${ROOT_DIR}/infra/terraform/scripts/wait-for-terraform-boundary.sh"

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

# MOI-565: dev push가 배포를 시작한다. 대신 PR CI가 같은 트리를 검증했음을
# AWS 자격증명 전에 증명해야 한다(dev ruleset: PR·build·strict 필수).
assert_contains "${WORKFLOW}" '^[[:space:]]{2}push:' "dev 배포는 dev push로 시작해야 한다."
assert_contains "${WORKFLOW}" 'branches:[[:space:]]*\[[[:space:]]*dev[[:space:]]*\]' "자동 배포는 dev로 제한해야 한다."
assert_not_contains "${WORKFLOW}" 'pull_request' "PR 이벤트는 배포 권한을 얻으면 안 된다."
assert_contains "${WORKFLOW}" 'verify-pr-ci\.sh "\$\{DEPLOY_SHA\}" dev' "PR CI가 검증한 트리인지 확인해야 한다."
assert_contains "${VERIFY_PR_CI}" 'merge_commit_sha == .*deploy_sha' "배포 SHA를 만든 머지 PR을 찾아야 한다."
assert_contains "${VERIFY_PR_CI}" 'head_tree.*!=.*deploy_tree' "PR head와 머지 커밋의 트리가 같아야 한다."
assert_contains "${VERIFY_PR_CI}" 'check_name="\$\{check_name\}"' "PR head의 required check 결과를 확인해야 한다."
assert_contains "${VERIFY_PR_CI}" '"success"' "required check 성공만 인정해야 한다."
assert_contains "${WORKFLOW}" 'wait-for-terraform-boundary\.sh' "적용되지 않은 Terraform 변경이 앞서면 기다려야 한다."
assert_contains "${BOUNDARY_WAITER}" 'git diff --quiet --no-renames "\$\{applied_sha\}" "\$\{deploy_sha\}"' "적용된 revision과 Terraform 소스를 rename-safe하게 비교해야 한다."
assert_contains "${TERRAFORM_APPLY_WORKFLOW}" ":\(exclude\)infra/terraform/tests' ':\(exclude\)infra/terraform/README.md'" "Terraform 생략 범위는 boundary waiter와 같아야 한다."
assert_contains "${BOUNDARY_WAITER}" ":\(exclude\)infra/terraform/tests' ':\(exclude\)infra/terraform/README.md'" "boundary waiter 범위는 Terraform 생략 범위와 같아야 한다."
assert_contains "${WORKFLOW}" 'Skip when a newer runtime revision is on dev' "mutation lock 뒤 더 오래된 revision이 최신 배포를 덮으면 안 된다."
assert_contains "${WORKFLOW}" 'git diff --name-only --no-renames "\$\{DEPLOY_SHA\}" "\$\{latest_sha\}"' "후속 docs-only 커밋 때문에 배포를 건너뛰면 안 된다."
assert_contains "${WORKFLOW}" 'deploy_required' "문서 전용 변경을 제외하는 gate가 있어야 한다."
assert_contains "${WORKFLOW}" '^[[:space:]]{4}concurrency:' "문서 전용 실행이 pending 배포를 대체하지 않도록 concurrency는 deploy job에 있어야 한다."
assert_contains "${WORKFLOW}" 'queue:[[:space:]]*max' "동일 환경의 pending mutation run을 새 run이 교체하면 안 된다."
assert_not_contains "${WORKFLOW}" '^concurrency:' "workflow-level concurrency는 문서 전용 실행이 pending 배포를 대체하게 만든다."
assert_contains "${WORKFLOW}" 'git diff --name-only --no-renames' "코드를 docs로 옮긴 rename도 배포 변경으로 판정해야 한다."

lock_line="$(line_of "${WORKFLOW}" '^[[:space:]]{4}concurrency:')"
wait_line="$(line_of "${WORKFLOW}" 'name: Wait for the Terraform boundary')"
if [ "${wait_line}" -ge "${lock_line}" ]; then
  fail "Terraform boundary 대기는 deploy lock 밖에서 해야 한다. apply·sync가 같은 lock을 쓴다."
fi
assert_contains "${WORKFLOW}" 'TERRAFORM_BOUNDARY_WAIT_SECONDS: "1"' "lock 안에서는 boundary를 기다리지 않고 확인만 해야 한다."
assert_contains "${VERIFY_PR_CI}" 'image_conclusion.*=.*"success"' "후보 이미지는 build와 같은 CI 실행 시도의 image job 성공에 묶어야 한다."
assert_contains "${VERIFY_PR_CI}" 'candidate_tag="tree-\$\{deploy_tree\}-run-\$\{run_id\}-\$\{run_attempt\}"' "후보 태그는 검증한 트리와 CI 실행에 묶어야 한다."
assert_contains "${CI_WORKFLOW}" 'run-\$\{GITHUB_RUN_ID\}-\$\{GITHUB_RUN_ATTEMPT\}' "PR CI는 실행별 후보 태그로 push해야 한다."
assert_not_contains "${CI_WORKFLOW}" 'Check for existing candidates' "다른 실행이 먼저 올린 후보 태그를 재사용하면 안 된다."
verify_line="$(line_of "${WORKFLOW}" 'name: Verify PR CI checked this exact tree')"
credentials_line="$(line_of "${WORKFLOW}" 'name: Configure AWS credentials')"
boundary_line="$(line_of "${WORKFLOW}" 'name: Wait for the Terraform boundary')"
config_line="$(line_of "${WORKFLOW}" 'name: Load Terraform deployment config')"
if [ "${verify_line}" -ge "${credentials_line}" ]; then
  fail "PR CI 검증은 AWS 자격증명보다 먼저 끝나야 한다."
fi
if [ "${boundary_line}" -ge "${config_line}" ]; then
  fail "배포 설정은 Terraform boundary 대기 뒤에 읽어야 한다."
fi

# PR CI가 만든 이미지를 digest 그대로 승격하고, 후보가 없을 때만 빌드한다.
assert_contains "${CI_WORKFLOW}" 'github\.event\.pull_request\.head\.repo\.full_name == github\.repository' "fork PR은 후보 이미지를 올리면 안 된다."
assert_contains "${CI_WORKFLOW}" "github\.event\.pull_request\.base\.ref == 'dev'" "후보 이미지는 dev 대상 PR에서만 만든다."
assert_contains "${CI_WORKFLOW}" "rev-parse 'HEAD\^\{tree\}'" "후보 이미지는 머지 트리로 태그해야 한다."
assert_contains "${CI_WORKFLOW}" "github\.actor != 'dependabot\[bot\]'" "Dependabot PR은 OIDC 없이 후보를 만들지 않는다."
assert_contains "${WORKFLOW}" 'copy-image-by-digest\.sh "\$\{candidate\}" "\$\{target\}"' "후보 이미지는 digest를 보존해 승격해야 한다."
promote_line="$(line_of "${WORKFLOW}" 'name: Promote PR-built candidate images')"
api_build_line="$(line_of "${WORKFLOW}" 'name: Build and push image')"
if [ "${promote_line}" -ge "${api_build_line}" ]; then
  fail "후보 승격은 fallback 빌드보다 먼저 와야 한다."
fi

assert_contains "${DOCKERFILE}" '^FROM .* AS core-api$' "Core API runtime target이 필요하다."
assert_contains "${DOCKERFILE}" '^FROM .* AS core-worker$' "Worker runtime target이 필요하다."
assert_contains "${DOCKERFILE}" ':core:core-api:bootJar' "공통 build가 Core API bootJar를 만들어야 한다."
assert_contains "${DOCKERFILE}" ':core:core-worker:bootJar' "공통 build가 Worker bootJar를 만들어야 한다."
# MOI-565: 학습 실행은 외부 연결 없는 local 프로필로만 하고, 운영 실행이 그 캐시를 읽어야 한다.
aot_trainings="$(grep -c -- '-XX:AOTCacheOutput=app.aot' "${DOCKERFILE}")"
aot_runtimes="$(grep -c -- '-XX:AOTCache=app.aot' "${DOCKERFILE}")"
local_trainings="$(grep -c -- '-Dspring.profiles.active=local' "${DOCKERFILE}")"
if [ "${aot_trainings}" -ne 2 ] || [ "${aot_runtimes}" -ne 2 ] || [ "${local_trainings}" -ne 2 ]; then
  fail "API·Worker 이미지는 각각 local 프로필로 AOT 캐시를 만들고 실행 시 그 캐시를 읽어야 한다."
fi
assert_not_contains "${DOCKERFILE}" 'spring\.profiles\.active=(dev|live|staging)' "AOT 학습 실행은 실제 환경 프로필로 외부 자원에 연결하면 안 된다."

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
