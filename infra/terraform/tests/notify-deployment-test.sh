#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
NOTIFY_SCRIPT="${ROOT_DIR}/infra/terraform/scripts/notify-deployment.sh"
DEV_NOTIFY_SCRIPT="${ROOT_DIR}/infra/terraform/scripts/notify-dev-deployment.sh"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIR}"' EXIT

# Fake curl: keeps the posted payload and exits with FAKE_CURL_EXIT.
cat > "${TEMP_DIR}/curl" <<'EOF'
#!/usr/bin/env bash
while [ "$#" -gt 0 ]; do
  if [ "$1" = "--data" ]; then
    printf '%s' "$2" > "${FAKE_PAYLOAD_FILE}"
    shift
  fi
  shift
done
exit "${FAKE_CURL_EXIT:-0}"
EOF
chmod +x "${TEMP_DIR}/curl"

export PATH="${TEMP_DIR}:${PATH}"
export FAKE_PAYLOAD_FILE="${TEMP_DIR}/payload.json"
export GITHUB_STEP_SUMMARY="${TEMP_DIR}/summary.md"

fail() {
  echo "배포 알림 테스트 실패: $1" >&2
  exit 1
}

reset_case() {
  rm -f "${FAKE_PAYLOAD_FILE}" "${GITHUB_STEP_SUMMARY}"
  unset SLACK_WEBHOOK_URL DEPLOY_STAGE IMAGE_SOURCE DEPLOY_REASON FAKE_CURL_EXIT \
    API_RESULT WORKER_RESULT SHARED_RESULT DEV_RESULT \
    CURRENT LATEST_SHA API_BUILD_RESULT API_CANDIDATE API_IMAGE_EXISTS WORKER_STEP_RESULT \
    WORKER_ENABLED WORKER_BUILD_SUCCEEDED WORKER_CANDIDATE WORKER_IMAGE CANDIDATE_TAG REPOSITORY_URL
  export DEPLOY_ENVIRONMENT=dev
  export DEPLOY_KIND=deployment
  export DEPLOY_OUTCOME=success
  export DEPLOY_SHA=0123456789abcdef0123456789abcdef01234567
  export DEPLOY_ACTOR=octocat
  export DEPLOY_RUN_URL=https://example.test/run
}

field_titles() {
  jq -r '[.blocks[1].fields[].text | split("\n")[0]] | join(",")' "${FAKE_PAYLOAD_FILE}"
}

# webhook 미설정은 배포를 실패시키지 않되 경고와 요약을 남긴다.
reset_case
output="$(bash "${NOTIFY_SCRIPT}")" || fail "webhook 미설정이 배포 알림 스텝을 실패시켰다."
grep -q '^::warning title=Slack notification not sent::' <<< "${output}" || fail "webhook 미설정 경고가 없다."
grep -q 'Slack notification not sent' "${GITHUB_STEP_SUMMARY}" || fail "webhook 미설정이 실행 요약에 없다."
[ ! -f "${FAKE_PAYLOAD_FILE}" ] || fail "webhook 미설정인데 전송을 시도했다."

# 기존 호출자(live 승격·rollback)는 기존 필드 그대로 받는다.
reset_case
export SLACK_WEBHOOK_URL=https://hooks.example.test/x API_RESULT=success WORKER_RESULT=success
bash "${NOTIFY_SCRIPT}" || fail "정상 전송이 실패했다."
[ "$(field_titles)" = "*SHA*,*Actor*,*Core API*,*Worker*,*Reason*" ] \
  || fail "기존 호출자의 필드가 바뀌었다: $(field_titles)"
[ "$(jq -r '.text' "${FAKE_PAYLOAD_FILE}")" = "[dev] deployment success (0123456789ab)" ] \
  || fail "기존 fallback text가 바뀌었다."
[ "$(jq '.blocks | length' "${FAKE_PAYLOAD_FILE}")" = 3 ] || fail "기존 block 구성이 바뀌었다."
# shellcheck disable=SC2016 # the backticks are literal Slack markup
expected_legacy='{"text":"[dev] deployment success (0123456789ab)","blocks":[{"type":"section","text":{"type":"mrkdwn","text":"*dev deployment*: success"}},{"type":"section","fields":[{"type":"mrkdwn","text":"*SHA*\n`0123456789abcdef0123456789abcdef01234567`"},{"type":"mrkdwn","text":"*Actor*\noctocat"},{"type":"mrkdwn","text":"*Core API*\nsuccess"},{"type":"mrkdwn","text":"*Worker*\nsuccess"},{"type":"mrkdwn","text":"*Reason*\nnot-provided"}]},{"type":"section","text":{"type":"mrkdwn","text":"<https://example.test/run|GitHub Actions run>"}}]}'
[ "$(jq -c . "${FAKE_PAYLOAD_FILE}")" = "${expected_legacy}" ] || fail "기존 호출자의 payload가 바뀌었다."

# 실패 단계와 이미지 출처는 주어질 때만 표시한다.
reset_case
export SLACK_WEBHOOK_URL=https://hooks.example.test/x DEPLOY_OUTCOME=failure \
  DEPLOY_STAGE="Terraform boundary" DEPLOY_REASON="not applied" IMAGE_SOURCE="Core API: built during deploy"
bash "${NOTIFY_SCRIPT}" || fail "단계 포함 전송이 실패했다."
[ "$(field_titles)" = "*SHA*,*Actor*,*Core API*,*Worker*,*Stage*,*Reason*,*Image*" ] \
  || fail "단계·이미지 필드가 없다: $(field_titles)"
jq -e '.blocks[1].fields[] | select(.text == "*Stage*\nTerraform boundary")' "${FAKE_PAYLOAD_FILE}" >/dev/null \
  || fail "실패 단계 값이 다르다."

# Terraform 결과는 API/Worker 대신 shared/dev 결과를 보인다.
reset_case
export SLACK_WEBHOOK_URL=https://hooks.example.test/x DEPLOY_KIND=terraform \
  SHARED_RESULT="no changes" DEV_RESULT=applied
bash "${NOTIFY_SCRIPT}" || fail "Terraform 알림 전송이 실패했다."
[ "$(field_titles)" = "*SHA*,*Actor*,*Shared*,*Dev*,*Reason*" ] \
  || fail "Terraform 알림 필드가 다르다: $(field_titles)"

# 건너뜀은 이유를 담은 한 줄과 실행 링크만 보낸다.
reset_case
export SLACK_WEBHOOK_URL=https://hooks.example.test/x DEPLOY_OUTCOME=skipped \
  DEPLOY_REASON="superseded by fedcba987654, which deploys instead"
bash "${NOTIFY_SCRIPT}" || fail "건너뜀 알림 전송이 실패했다."
[ "$(jq '.blocks | length' "${FAKE_PAYLOAD_FILE}")" = 2 ] || fail "건너뜀 알림은 짧아야 한다."
jq -e '.blocks[0].text.text | contains("skipped") and contains("superseded by fedcba987654")' \
  "${FAKE_PAYLOAD_FILE}" >/dev/null || fail "건너뜀 이유가 없다."

# 축약은 dev 배포에만 쓴다. live rollback이 skipped여도 기존 메시지를 유지한다.
reset_case
export SLACK_WEBHOOK_URL=https://hooks.example.test/x DEPLOY_ENVIRONMENT=live DEPLOY_KIND=rollback \
  DEPLOY_OUTCOME=skipped API_RESULT=skipped WORKER_RESULT=skipped
bash "${NOTIFY_SCRIPT}" || fail "rollback 알림 전송이 실패했다."
[ "$(field_titles)" = "*SHA*,*Actor*,*Core API*,*Worker*,*Reason*" ] \
  || fail "skipped rollback 알림이 축약됐다: $(field_titles)"

# 전송 실패는 스텝을 실패시키고(continue-on-error로 격리) 경고와 요약을 남긴다.
reset_case
export SLACK_WEBHOOK_URL=https://hooks.example.test/x FAKE_CURL_EXIT=22
if output="$(bash "${NOTIFY_SCRIPT}")"; then
  fail "전송 실패를 성공으로 보고했다."
fi
grep -q '^::warning title=Slack notification failed::' <<< "${output}" || fail "전송 실패 경고가 없다."
grep -q 'Slack notification failed' "${GITHUB_STEP_SUMMARY}" || fail "전송 실패가 실행 요약에 없다."

field_value() {
  jq -r --arg title "$1" '.blocks[1].fields[] | select(.text | startswith("*" + $title + "*\n")) | .text | split("\n")[1:] | join("\n")' \
    "${FAKE_PAYLOAD_FILE}"
}

candidate_tag=tree-0123456789abcdef0123456789abcdef01234567-run-987-2

# dev 배포: PR CI 이미지를 승격하면 이미지를 만든 실행을 연결한다.
reset_case
export SLACK_WEBHOOK_URL=https://hooks.example.test/x CURRENT=true API_RESULT=success \
  API_CANDIDATE=promoted API_IMAGE_EXISTS=true WORKER_STEP_RESULT=success WORKER_ENABLED=true \
  WORKER_BUILD_SUCCEEDED=true WORKER_CANDIDATE=promoted WORKER_IMAGE=existing \
  CANDIDATE_TAG="${candidate_tag}" REPOSITORY_URL=https://github.com/o/r
bash "${DEV_NOTIFY_SCRIPT}" || fail "dev 배포 알림 전송이 실패했다."
expected_run="PR CI <https://github.com/o/r/actions/runs/987/attempts/2|run 987 attempt 2>"
[ "$(field_value Image)" = "Core API: ${expected_run}"$'\n'"Worker: ${expected_run}" ] \
  || fail "승격 이미지의 PR CI 실행 연결이 다르다: $(field_value Image)"

# dev 배포: 후보가 없어 빌드했고 API 안정화 뒤 Worker 빌드가 실패했다.
reset_case
export SLACK_WEBHOOK_URL=https://hooks.example.test/x CURRENT=true DEPLOY_OUTCOME=failure \
  API_RESULT=success API_BUILD_RESULT=success API_CANDIDATE=none API_IMAGE_EXISTS=false \
  WORKER_STEP_RESULT=skipped WORKER_ENABLED=true WORKER_BUILD_SUCCEEDED=false WORKER_CANDIDATE=none \
  WORKER_IMAGE=built
bash "${DEV_NOTIFY_SCRIPT}" || fail "Worker 빌드 실패 알림 전송이 실패했다."
[ "$(field_value Worker)" = image-build-failed ] || fail "Worker 빌드 실패가 $(field_value Worker)로 표시됐다."
[ "$(field_value Image)" = "Core API: built during deploy" ] \
  || fail "실패한 Worker 빌드를 이미지 출처로 표시했다: $(field_value Image)"

# dev 배포: Core API 빌드 실패를 '빌드함'으로 표시하지 않는다.
reset_case
export SLACK_WEBHOOK_URL=https://hooks.example.test/x CURRENT=true DEPLOY_OUTCOME=failure \
  API_BUILD_RESULT=failure API_CANDIDATE=missing API_IMAGE_EXISTS=false WORKER_ENABLED=true
bash "${DEV_NOTIFY_SCRIPT}" || fail "API 빌드 실패 알림 전송이 실패했다."
[ "$(field_value Image)" = "Core API: build failure" ] || fail "API 빌드 실패 표시가 다르다: $(field_value Image)"

# dev 배포: 재시도에서 남은 Worker 이미지를 재사용하면 빌드했다고 하지 않는다.
reset_case
export SLACK_WEBHOOK_URL=https://hooks.example.test/x CURRENT=true API_RESULT=success \
  API_CANDIDATE=existing API_IMAGE_EXISTS=true WORKER_STEP_RESULT=success WORKER_ENABLED=true \
  WORKER_BUILD_SUCCEEDED=true WORKER_CANDIDATE=expired WORKER_IMAGE=existing
bash "${DEV_NOTIFY_SCRIPT}" || fail "재시도 알림 전송이 실패했다."
[ "$(field_value Image)" = "Core API: existing deploy image"$'\n'"Worker: existing deploy image" ] \
  || fail "재사용 이미지 표시가 다르다: $(field_value Image)"

# dev 배포: 더 새 커밋에 밀리면 성공 대신 skipped 한 줄을 보낸다.
reset_case
export SLACK_WEBHOOK_URL=https://hooks.example.test/x CURRENT=false \
  LATEST_SHA=fedcba9876543210fedcba9876543210fedcba98
bash "${DEV_NOTIFY_SCRIPT}" || fail "건너뜀 알림 전송이 실패했다."
jq -e '.text == "[dev] deployment skipped (0123456789ab)"' "${FAKE_PAYLOAD_FILE}" >/dev/null \
  || fail "밀린 배포를 skipped로 보내지 않았다."
jq -e '.blocks[0].text.text | contains("superseded by fedcba987654")' "${FAKE_PAYLOAD_FILE}" >/dev/null \
  || fail "밀린 배포의 대체 커밋이 없다."

echo "notify-deployment tests passed"
