#!/usr/bin/env bash

# MOI-589: a retried commit reuses its recorded task definitions and the
# bundle stays immutable.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPTS="${ROOT_DIR}/infra/terraform/scripts"
WORKFLOW="${ROOT_DIR}/.github/workflows/deploy-aws.yml"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIR}"' EXIT

fail() {
  echo "배포 기록 계약 위반: $1" >&2
  exit 1
}

SHA="0123456789abcdef0123456789abcdef01234567"
PREFIX=/moimyeon/dev/deployments
DIGEST="$(printf 'a%.0s' {1..64})"
API_IMAGE="111111111111.dkr.ecr.ap-northeast-2.amazonaws.com/moimyeon-dev-core-api@sha256:${DIGEST}"
WORKER_IMAGE="111111111111.dkr.ecr.ap-northeast-2.amazonaws.com/moimyeon-dev-core-worker@sha256:${DIGEST}"
API_ARN="arn:aws:ecs:ap-northeast-2:111111111111:task-definition/moimyeon-dev-core-api:41"
WORKER_ARN="arn:aws:ecs:ap-northeast-2:111111111111:task-definition/moimyeon-dev-core-worker:17"
STORE="${TEMP_DIR}/ssm"
mkdir -p "${STORE}" "${TEMP_DIR}/bin"

# Fake aws: SSM parameters live as files.
cat > "${TEMP_DIR}/bin/aws" <<FAKE
#!/usr/bin/env bash
set -euo pipefail
name=""; value=""
args=("\$@")
for ((i = 0; i < \${#args[@]}; i++)); do
  case "\${args[i]}" in
    --name) name="\${args[i+1]}" ;;
    --value) value="\${args[i+1]}" ;;
  esac
done
file="${STORE}/\$(tr '/' '_' <<< "\${name}")"
case "\$1 \$2" in
  "ssm get-parameter")
    [ "\${FAKE_SSM:-ok}" = ok ] || { echo "An error occurred (ThrottlingException): Rate exceeded" >&2; exit 254; }
    [ -f "\${file}" ] || { echo "An error occurred (ParameterNotFound) when calling the GetParameter operation" >&2; exit 254; }
    case "\$*" in *Parameter.Name*) echo "\${name}" ;; *) cat "\${file}" ;; esac ;;
  "ssm put-parameter")
    [ ! -f "\${file}" ] || exit 254
    printf '%s' "\${value}" > "\${file}" ;;
  *) exit 99 ;;
esac
FAKE
chmod +x "${TEMP_DIR}/bin/aws"
export PATH="${TEMP_DIR}/bin:${PATH}"

# --- find-deployment-bundle.sh ----------------------------------------------
result="$(bash "${SCRIPTS}/find-deployment-bundle.sh" "${PREFIX}" dev "${SHA}")"
[ "${result}" = "recorded=false" ] || fail "기록이 없으면 recorded=false여야 한다."

if FAKE_SSM=throttled bash "${SCRIPTS}/find-deployment-bundle.sh" "${PREFIX}" dev "${SHA}" >/dev/null 2>&1; then
  fail "조회 오류를 '기록 없음'으로 읽으면 새 태스크 정의를 등록해 기록과 충돌한다."
fi

bash "${SCRIPTS}/record-deployment-bundle.sh" "${PREFIX}" dev "${SHA}" \
  "${API_IMAGE}" "${API_ARN}" "${WORKER_IMAGE}" "${WORKER_ARN}" >/dev/null
result="$(bash "${SCRIPTS}/find-deployment-bundle.sh" "${PREFIX}" dev "${SHA}")"
grep -qx "recorded=true" <<< "${result}" || fail "기록이 있으면 recorded=true여야 한다."
grep -qx "api_task_definition_arn=${API_ARN}" <<< "${result}" || fail "기록된 API 태스크 정의를 돌려줘야 한다."
grep -qx "worker_task_definition_arn=${WORKER_ARN}" <<< "${result}" || fail "기록된 Worker 태스크 정의를 돌려줘야 한다."

# --- record-deployment-bundle.sh: immutable, idempotent ----------------------
bash "${SCRIPTS}/record-deployment-bundle.sh" "${PREFIX}" dev "${SHA}" \
  "${API_IMAGE}" "${API_ARN}" "${WORKER_IMAGE}" "${WORKER_ARN}" >/dev/null \
  || fail "같은 커밋을 같은 태스크 정의로 다시 기록하면 성공해야 한다."
if bash "${SCRIPTS}/record-deployment-bundle.sh" "${PREFIX}" dev "${SHA}" \
  "${API_IMAGE}" "${API_ARN%:41}:42" "${WORKER_IMAGE}" "${WORKER_ARN}" >/dev/null 2>&1; then
  fail "같은 커밋에 다른 태스크 정의를 기록하면 안 된다."
fi

# --- deploy-aws.yml wiring ---------------------------------------------------
grep -q 'find-deployment-bundle\.sh' "${WORKFLOW}" || fail "dev 배포는 기록된 배포를 먼저 찾아야 한다."
[ "$(grep -c 'Reusing recorded .* task definition' "${WORKFLOW}")" -eq 2 ] \
  || fail "재시도는 API·Worker 모두 기록된 태스크 정의를 다시 써야 한다."
grep -q 'The recorded bundle for this commit includes a Notification Worker' "${WORKFLOW}" \
  || fail "Worker를 끈 뒤 Worker가 있는 기록을 재배포하면 무엇이든 바꾸기 전에 멈춰야 한다."
grep -q 'The recorded bundle for this commit has no Notification Worker' "${WORKFLOW}" \
  || fail "Worker를 켠 뒤 Worker가 없는 기록을 재배포하면 무엇이든 바꾸기 전에 멈춰야 한다."
verify_line="$(grep -n 'name: Verify recorded task definitions match this run' "${WORKFLOW}" | cut -d: -f1)"
api_update_line="$(grep -n 'name: Deploy ECS service' "${WORKFLOW}" | cut -d: -f1)"
[ -n "${verify_line}" ] && [ "${verify_line}" -lt "${api_update_line}" ] \
  || fail "기록된 이미지 확인은 어떤 서비스든 바꾸기 전에 해야 한다."
find_line="$(grep -n 'name: Find an already recorded deployment bundle' "${WORKFLOW}" | cut -d: -f1)"
register_line="$(grep -n 'name: Register task definition revision' "${WORKFLOW}" | cut -d: -f1)"
[ "${find_line}" -lt "${register_line}" ] || fail "기록 확인은 태스크 정의 등록보다 먼저여야 한다."

echo "배포 기록 계약을 만족한다."
