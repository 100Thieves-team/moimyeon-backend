#!/usr/bin/env bash

# MOI-592: one rule decides whether a change can affect a running environment.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
RULE="${ROOT_DIR}/.github/scripts/runtime-changes.sh"

fail() {
  echo "런타임 변경 판정 계약 위반: $1" >&2
  exit 1
}

expect() {
  local environment="$1" expected="$2" reason="$3"
  shift 3
  local actual
  actual="$(printf '%s\n' "$@" | bash "${RULE}" "${environment}")"
  [ "${actual}" = "runtime=${expected}" ] || fail "${reason} (${environment}: $*, ${actual})"
}

for environment in dev live; do
  expect "${environment}" false "문서만 바뀌면 배포하지 않는다" docs/a.md README.md core/x/NOTE.mdx
  expect "${environment}" false "하네스·작업 기록은 런타임이 아니다" .agents/skills/x/SKILL.md .claude/settings.json .worklog/x/plan.md .githooks/pre-commit
  expect "${environment}" false "저장소 도구 설정은 런타임이 아니다" .gitleaks.toml .review-swarm.yaml .editorconfig
  expect "${environment}" false "리뷰·문서 전용 워크플로는 배포에 쓰이지 않는다" .github/workflows/review-swarm.yml .github/workflows/terraform-plan.yml
  expect "${environment}" false "CI 보조 스크립트는 런타임이 아니다" .github/scripts/check_flyway_migrations.py
  expect "${environment}" false "테스트 코드는 boot jar에 들어가지 않는다" core/core-api/src/test/kotlin/X.kt tests/api-docs/build.gradle.kts
  expect "${environment}" false "Terraform 계약 테스트는 런타임이 아니다" infra/terraform/tests/x.sh infra/terraform/README.md
  expect "${environment}" false "모니터링 호스트 설정은 앱 배포가 아니다" infra/observability/prometheus.yaml
  expect "${environment}" false "빈 변경은 배포하지 않는다" ""

  expect "${environment}" true "앱 코드는 런타임이다" core/core-api/src/main/kotlin/X.kt
  expect "${environment}" true "빌드 설정은 런타임이다" build.gradle.kts
  expect "${environment}" true "이미지 정의는 런타임이다" Dockerfile
  expect "${environment}" true "배포·빌드 워크플로는 배포로 검증한다" .github/workflows/deploy-aws.yml
  expect "${environment}" true "배포·빌드 워크플로는 배포로 검증한다" .github/workflows/ci.yml
  expect "${environment}" true "배포 판정 규칙 자신은 배포로 검증한다" .github/scripts/runtime-changes.sh
  expect "${environment}" true "배포 스크립트는 런타임이다" infra/terraform/scripts/deploy-ecs-image.sh
  expect "${environment}" true "공용 Terraform 모듈은 태스크 정의를 바꿀 수 있다" infra/terraform/modules/moimyeon-environment/ecs.tf
  expect "${environment}" true "공유 환경 Terraform은 보수적으로 배포한다" infra/terraform/envs/shared/main.tf
  expect "${environment}" true "목록에 없는 경로는 배포한다" some/new/path.txt
  expect "${environment}" true "런타임 변경이 하나라도 섞이면 배포한다" docs/a.md core/core-api/src/main/kotlin/X.kt
done

expect dev false "live 값만 바뀌면 dev는 배포하지 않는다" infra/terraform/envs/live/live.tfvars
expect dev true "dev 값이 바뀌면 dev를 배포한다" infra/terraform/envs/dev/dev.tfvars
expect live false "dev 값만 바뀌면 live는 승격하지 않는다" infra/terraform/envs/dev/dev.tfvars
expect live true "live 값이 바뀌면 live를 승격한다" infra/terraform/envs/live/live.tfvars

expect dev true "CI 보조 스크립트도 목록에 없으면 배포한다" .github/scripts/new-deploy-helper.sh

# tests/ 제외의 전제: 실행용 구성은 :tests: 프로젝트를 쓰지 않는다.
if grep -rEn --include=build.gradle.kts '(implementation|runtimeOnly|api)\(project\(":tests:' "${ROOT_DIR}" >/dev/null; then
  fail "실행용 의존이 :tests: 프로젝트를 쓰면 tests/ 변경도 런타임 변경이다"
fi

if printf 'x\n' | bash "${RULE}" staging >/dev/null 2>&1; then
  fail "알 수 없는 환경은 거부해야 한다"
fi

echo "런타임 변경 판정 계약을 만족한다."
