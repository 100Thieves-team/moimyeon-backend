#!/usr/bin/env bash

# MOI-581: deploy-ecs-image.sh waits for ECS stability with an explicit deadline
# instead of the 10-minute built-in waiter. The loop lives in a shared library
# the script sources; exercise it with a fake aws CLI so no AWS access is needed.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
WAIT_LIB="${ROOT_DIR}/infra/terraform/scripts/lib/ecs-stable-wait.sh"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIR}"' EXIT

fail() {
  echo "ECS 안정화 대기 테스트 실패: $1" >&2
  exit 1
}

# Fake aws: answers describe-services with the next line of FAKE_STATES, keeping
# the last line once the list runs out.
cat > "${TEMP_DIR}/aws" <<'EOF'
#!/usr/bin/env bash
count_file="${FAKE_DIR}/calls"
calls=$(( $(cat "${count_file}" 2>/dev/null || echo 0) + 1 ))
echo "${calls}" > "${count_file}"
total=$(printf '%s\n' "${FAKE_STATES}" | wc -l | tr -d ' ')
line=$(( calls < total ? calls : total ))
printf '%s\n' "${FAKE_STATES}" | sed -n "${line}p"
EOF
chmod +x "${TEMP_DIR}/aws"

run_wait() {
  rm -f "${TEMP_DIR}/calls"
  env PATH="${TEMP_DIR}:${PATH}" FAKE_DIR="${TEMP_DIR}" FAKE_STATES="$1" \
    ECS_STABLE_TIMEOUT_SECONDS="$2" ECS_STABLE_POLL_SECONDS=1 \
    bash -c 'set -euo pipefail; cluster=c; service=s; source "$1"; wait_for_service_stable' _ "${WAIT_LIB}"
}

# 두 배포가 겹친 동안은 기다리고, 하나만 남아 실행 수가 원하는 수와 같아지면 성공한다.
run_wait $'2\t1\t1\n2\t2\t1\n1\t0\t1\n1\t1\t1' 30 2>/dev/null || fail "안정화된 서비스를 실패로 판정했다."
[ "$(cat "${TEMP_DIR}/calls")" = 4 ] || fail "안정화 전에 대기를 끝냈다: $(cat "${TEMP_DIR}/calls")회 조회"

# 기한 안에 안정되지 않으면 실패한다.
if run_wait $'2\t1\t1' 3 2>/dev/null; then
  fail "기한이 지나도 안정되지 않은 서비스를 성공으로 판정했다."
fi

# 잘못된 기한 값은 거부한다.
if output="$(run_wait $'1\t1\t1' 0 2>&1)"; then
  fail "0초 기한을 받아들였다."
fi
grep -q 'ECS_STABLE_TIMEOUT_SECONDS must be a positive integer' <<< "${output}" \
  || fail "잘못된 기한 값의 원인을 알리지 않았다."

echo "deploy-ecs stable wait tests passed"
