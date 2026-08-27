#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORKFLOW="${ROOT_DIR}/.github/workflows/api-docs-pages.yml"
DIFF_SCRIPT="${ROOT_DIR}/.github/scripts/openapi_operation_diff.py"
NOTIFY_SCRIPT="${ROOT_DIR}/.github/scripts/notify-api-spec-change.sh"
NOTIFY_TEST="${ROOT_DIR}/.github/scripts/test_notify_api_spec_change.py"

fail() {
  echo "API 스펙 알림 계약 위반: $1" >&2
  exit 1
}

assert_contains() {
  local file="$1"
  local pattern="$2"
  local message="$3"

  grep -Eq -- "${pattern}" "${file}" || fail "${message}"
}

line_of() {
  local file="$1"
  local pattern="$2"

  grep -nEm1 -- "${pattern}" "${file}" | cut -d: -f1
}

assert_not_contains() {
  local file="$1"
  local pattern="$2"
  local message="$3"

  if grep -Eq -- "${pattern}" "${file}"; then
    fail "${message}"
  fi
}

assert_contains "${WORKFLOW}" 'refs/heads/gh-pages:refs/remotes/origin/gh-pages' "직전 공개 스펙은 gh-pages에서 읽어야 한다."
assert_contains "${WORKFLOW}" 'core/core-enum/src/\*\*' "OpenAPI 보정에 쓰는 core-enum 변경도 워크플로를 깨워야 한다."
assert_contains "${WORKFLOW}" 'openapi_operation_diff\.py' "OpenAPI operation 비교기를 실행해야 한다."
assert_contains "${WORKFLOW}" 'notify-api-spec-change\.sh' "변경된 API를 Slack으로 보내야 한다."
assert_contains "${WORKFLOW}" 'SLACK_API_SPEC_WEBHOOK_URL' "알림 전용 webhook secret을 사용해야 한다."
assert_contains "${WORKFLOW}" "steps\.api_diff\.outputs\.changed == 'true'" "실제 스펙 변경이 있을 때만 알림을 보내야 한다."
assert_contains "${WORKFLOW}" "if:.*github\.ref_name == 'dev'" "API 스펙 변경 알림은 dev에서만 보내야 한다."
assert_not_contains "${WORKFLOW}" "if:.*github\.ref_name == 'main'" "main 승격에서 API 스펙 변경 알림을 중복하면 안 된다."
assert_contains "${WORKFLOW}" 'continue-on-error:[[:space:]]*true' "Slack 실패가 API 문서 배포를 덮으면 안 된다."
assert_contains "${WORKFLOW}" '^[[:space:]]+contents:[[:space:]]*write' "gh-pages 배포 job에만 contents write가 있어야 한다."
assert_contains "${WORKFLOW}" 'uses: actions/checkout@[0-9a-f]{40}' "checkout Action은 full SHA로 고정해야 한다."
assert_contains "${WORKFLOW}" 'uses: actions/setup-java@[0-9a-f]{40}' "setup-java Action은 full SHA로 고정해야 한다."
assert_contains "${WORKFLOW}" 'uses: gradle/actions/setup-gradle@[0-9a-f]{40}' "setup-gradle Action은 full SHA로 고정해야 한다."
assert_contains "${WORKFLOW}" 'uses: peaceiris/actions-gh-pages@[0-9a-f]{40}' "gh-pages Action은 full SHA로 고정해야 한다."
assert_not_contains "${WORKFLOW}" 'uses: [^ ]+@v[0-9]' "외부 Action에 이동 가능한 version tag를 쓰면 안 된다."
assert_contains "${DIFF_SCRIPT}" 'safe_load' "OpenAPI YAML은 안전한 loader로 읽어야 한다."
assert_contains "${NOTIFY_SCRIPT}" 'curl --fail' "Slack HTTP 실패를 감지해야 한다."
assert_contains "${NOTIFY_SCRIPT}" 'jq -n' "Slack payload는 문자열 결합이 아니라 jq로 생성해야 한다."
assert_contains "${NOTIFY_TEST}" 'ThreadingHTTPServer' "Slack payload를 실제 로컬 HTTP 수신기로 검증해야 한다."

publish_line="$(line_of "${WORKFLOW}" 'name: Publish to gh-pages')"
notify_line="$(line_of "${WORKFLOW}" 'name: Notify API spec changes to Slack')"
if [ "${publish_line}" -ge "${notify_line}" ]; then
  fail "게시 실패 후 재실행에서 Slack 알림을 중복하지 않도록 Pages 게시 뒤 알림해야 한다."
fi

echo "API 스펙 알림 워크플로 계약을 만족한다."
