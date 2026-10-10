#!/usr/bin/env bash

# MOI-590: the Worker is replaced only when this commit can change it, and an
# unchanged Worker is carried into the commit's deployment bundle.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
RESOLVE="${ROOT_DIR}/infra/terraform/scripts/resolve-running-service.sh"
INPUT="${ROOT_DIR}/.github/scripts/runtime-input.sh"
WORKFLOW="${ROOT_DIR}/.github/workflows/deploy-aws.yml"
CI_WORKFLOW="${ROOT_DIR}/.github/workflows/ci.yml"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIR}"' EXIT

fail() {
  echo "Worker 선택 교체 계약 위반: $1" >&2
  exit 1
}

mkdir -p "${TEMP_DIR}/bin"
WORKER_ARN="arn:aws:ecs:ap-northeast-2:111111111111:task-definition/moimyeon-dev-core-worker:17"
# Dev registers commit tags, not digests.
WORKER_TAG="111111111111.dkr.ecr.ap-northeast-2.amazonaws.com/moimyeon-dev-core-worker:dev-0123456789ab"

# Fake aws/docker: answers come from FAKE_* variables. docker answers a digest
# for --format '{{.Manifest.Digest}}' and a label document otherwise, keyed by
# the image reference in FAKE_DIGESTS / FAKE_LABELS ("image=value" lines).
cat > "${TEMP_DIR}/bin/aws" <<'FAKE'
#!/usr/bin/env bash
case "$1 $2" in
  "ecs describe-services") echo "${FAKE_SERVICES}" ;;
  "ecs describe-task-definition")
    case "$*" in
      *"--query taskDefinition"*) jq '.taskDefinition' <<< "${FAKE_TASK_DEFINITION}" ;;
      *) echo "${FAKE_TASK_DEFINITION}" ;;
    esac ;;
  *) exit 99 ;;
esac
FAKE
cat > "${TEMP_DIR}/bin/docker" <<'FAKE'
#!/usr/bin/env bash
image="$4"
case "$*" in
  *"{{.Manifest.Digest}}"*)
    value="$(sed -n "s|^${image}=||p" <<< "${FAKE_DIGESTS:-}")"
    [ -n "${value}" ] || exit 1
    echo "${value}" ;;
  *)
    [ -n "${FAKE_INSPECT:-}" ] && { echo "${FAKE_INSPECT}"; exit 0; }
    value="$(sed -n "s|^${image}=||p" <<< "${FAKE_LABELS:-}")"
    jq -cn --arg label "${value}" 'if $label == "" then {config:{Labels:{}}} else {config:{Labels:{"org.moimyeon.runtime-input":$label}}} end' ;;
esac
FAKE
chmod +x "${TEMP_DIR}/bin/aws" "${TEMP_DIR}/bin/docker"
export PATH="${TEMP_DIR}/bin:${PATH}"

# --- resolve-running-service.sh ---------------------------------------------
settled="{\"services\":[{\"runningCount\":1,\"desiredCount\":1,\"pendingCount\":0,\"deployments\":[{\"status\":\"PRIMARY\",\"rolloutState\":\"COMPLETED\",\"taskDefinition\":\"${WORKER_ARN}\"}]}]}"
task="{\"taskDefinition\":{\"containerDefinitions\":[{\"name\":\"core-worker\",\"image\":\"${WORKER_TAG}\"},{\"name\":\"log-router\",\"image\":\"fluent\"}]}}"
result="$(FAKE_SERVICES="${settled}" FAKE_TASK_DEFINITION="${task}" bash "${RESOLVE}" cluster core-worker core-worker)" \
  || fail "안정된 서비스의 태그 이미지(dev 실제 형태)를 읽어야 한다."
grep -qx "task_definition_arn=${WORKER_ARN}" <<< "${result}" || fail "실행 중인 태스크 정의를 돌려줘야 한다."
grep -qx "image_uri=${WORKER_TAG}" <<< "${result}" || fail "등록된 이미지 참조를 그대로 돌려줘야 한다."

rolling="{\"services\":[{\"deployments\":[{\"status\":\"PRIMARY\",\"rolloutState\":\"IN_PROGRESS\",\"taskDefinition\":\"${WORKER_ARN}\"},{\"status\":\"ACTIVE\",\"taskDefinition\":\"${WORKER_ARN%:17}:16\"}]}]}"
if FAKE_SERVICES="${rolling}" FAKE_TASK_DEFINITION="${task}" bash "${RESOLVE}" cluster core-worker core-worker >/dev/null 2>&1; then
  fail "교체 중인 서비스를 실행 상태로 읽으면 안 된다."
fi
crashing="{\"services\":[{\"runningCount\":0,\"desiredCount\":1,\"pendingCount\":1,\"deployments\":[{\"status\":\"PRIMARY\",\"rolloutState\":\"COMPLETED\",\"taskDefinition\":\"${WORKER_ARN}\"}]}]}"
if FAKE_SERVICES="${crashing}" FAKE_TASK_DEFINITION="${task}" bash "${RESOLVE}" cluster core-worker core-worker >/dev/null 2>&1; then
  fail "태스크가 다 떠 있지 않은 서비스를 안정 상태로 읽으면 안 된다."
fi
if FAKE_SERVICES="${settled}" FAKE_TASK_DEFINITION='{"taskDefinition":{"containerDefinitions":[]}}' \
  bash "${RESOLVE}" cluster core-worker core-worker >/dev/null 2>&1; then
  fail "컨테이너가 없으면 실행 상태를 만들면 안 된다."
fi

# --- runtime-input.sh ---------------------------------------------------------
printf 'jar-a' > "${TEMP_DIR}/a.jar"
printf 'jar-b' > "${TEMP_DIR}/b.jar"
hash_a="$(bash "${INPUT}" hash "${TEMP_DIR}/a.jar")"
[ "${hash_a}" = "$(bash "${INPUT}" hash "${TEMP_DIR}/a.jar")" ] || fail "같은 입력은 같은 해시여야 한다."
[ "${hash_a}" != "$(bash "${INPUT}" hash "${TEMP_DIR}/b.jar")" ] || fail "jar가 바뀌면 해시가 바뀌어야 한다."
[[ "${hash_a}" =~ ^[0-9a-f]{64}$ ]] || fail "해시는 sha256 hex여야 한다."

single='{"config":{"Labels":{"org.moimyeon.runtime-input":"abc"}}}'
[ "$(FAKE_INSPECT="${single}" bash "${INPUT}" read image)" = abc ] || fail "단일 플랫폼 이미지의 label을 읽어야 한다."
index='{"linux/amd64":{"config":{"Labels":{"org.moimyeon.runtime-input":"def"}}},"unknown/unknown":{"config":{}}}'
[ "$(FAKE_INSPECT="${index}" bash "${INPUT}" read image)" = def ] || fail "provenance index의 linux/amd64 label을 읽어야 한다."
[ -z "$(FAKE_INSPECT='{"config":{"Labels":null}}' bash "${INPUT}" read image)" ] || fail "label이 없으면 빈 값이어야 한다."

# --- decide-worker-change.sh -------------------------------------------------
DECIDE="${ROOT_DIR}/infra/terraform/scripts/decide-worker-change.sh"
SHA="fedcba9876543210fedcba9876543210fedcba98"
REPO="111111111111.dkr.ecr.ap-northeast-2.amazonaws.com/moimyeon-dev-core-worker"
NEW_IMAGE="${REPO}:dev-${SHA::12}"
MARKER="${REPO}:deployed-dev-${SHA::12}"
TEMPLATE_ARN="arn:aws:ecs:ap-northeast-2:111111111111:task-definition/moimyeon-dev-core-worker:9"
template_doc() {
  jq -cn --arg arn "${TEMPLATE_ARN}" --arg env "${1:-1}" '{taskDefinitionArn:$arn, family:"moimyeon-dev-core-worker", status:"ACTIVE",
    cpu:"256", memory:"512", containerDefinitions:[{name:"core-worker", image:"repo:dev", environment:[{name:"A", value:$env}]}]}'
}
template_doc 1 > "${TEMP_DIR}/template.json"
running_task() {
  jq -cn --arg image "${WORKER_TAG}" --arg env "${1:-1}" --arg arn "${WORKER_ARN}" '{taskDefinition:{taskDefinitionArn:$arn, family:"moimyeon-dev-core-worker",
    status:"ACTIVE", revision:17, cpu:"256", memory:"512", containerDefinitions:[{name:"core-worker", image:$image,
    environment:[{name:"A", value:$env}, {name:"APP_RELEASE", value:"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}]}]}}'
}
decide() {
  env DEPLOY_SHA="${SHA}" WORKER_IMAGE_URI="${NEW_IMAGE}" WORKER_REPOSITORY="${REPO}" CLUSTER=cluster \
    WORKER_SERVICE=core-worker WORKER_CONTAINER=core-worker WORKER_TEMPLATE="${TEMPLATE_ARN}" \
    TEMPLATE_FILE="${TEMP_DIR}/template.json" WORK_DIR="${TEMP_DIR}" WORKER_CANDIDATE=promoted \
    FAKE_SERVICES="${settled}" FAKE_TASK_DEFINITION="$(running_task 1)" \
    FAKE_LABELS="${NEW_IMAGE}=same"$'\n'"${WORKER_TAG}=same" "$@" bash "${DECIDE}"
}
expect_decision() {
  local expected="$1" reason="$2"
  shift 2
  local output
  output="$(decide "$@" 2>/dev/null)" || fail "${reason}: 판단이 실패했다"
  grep -qx "deploy=${expected}" <<< "${output}" || fail "${reason}: $(tr '\n' ' ' <<< "${output}")"
}

expect_decision false "코드·원본 틀·안정 상태가 같으면 유지한다"
output="$(decide)"
grep -qx "running_task_definition_arn=${WORKER_ARN}" <<< "${output}" || fail "유지할 Worker의 태스크 정의를 내보내야 한다."
grep -qx "running_image_uri=${WORKER_TAG}" <<< "${output}" || fail "유지할 Worker의 이미지를 내보내야 한다."
expect_decision true "후보 승격이 아니면 교체한다" WORKER_CANDIDATE=none
expect_decision true "후보가 만료됐으면 교체한다" WORKER_CANDIDATE=expired
expect_decision true "코드가 바뀌면 교체한다" FAKE_LABELS="${NEW_IMAGE}=new"$'\n'"${WORKER_TAG}=old"
expect_decision true "실행 중 이미지에 label이 없으면 교체한다" FAKE_LABELS="${NEW_IMAGE}=same"
expect_decision true "새 이미지에 label이 없으면 교체한다" FAKE_LABELS="${WORKER_TAG}=same"
expect_decision true "실행 중 Worker가 안정 상태가 아니면 교체한다" FAKE_SERVICES="${rolling}"
expect_decision true "원본 틀이 바뀌면 교체한다" FAKE_TASK_DEFINITION="$(running_task 2)"
expect_decision true "이전 시도가 이 커밋 Worker를 배포했으면 다시 교체한다" \
  FAKE_DIGESTS="${MARKER}=sha256:new"$'\n'"${NEW_IMAGE}=sha256:new"
expect_decision false "이전 시도가 유지한 Worker가 그대로 돌면 다시 유지한다" WORKER_CANDIDATE=none \
  FAKE_DIGESTS="${MARKER}=sha256:old"$'\n'"${NEW_IMAGE}=sha256:new"$'\n'"${WORKER_TAG}=sha256:old"
if decide FAKE_DIGESTS="${MARKER}=sha256:old"$'\n'"${NEW_IMAGE}=sha256:new"$'\n'"${WORKER_TAG}=sha256:rolled-back" >/dev/null 2>&1; then
  fail "유지했던 커밋을 재시도할 때 다른 Worker가 돌면 바꾸기 전에 멈춰야 한다(롤백 뒤 재실행)."
fi

# --- wiring ---------------------------------------------------------------------
line_of() { grep -nF -- "$1" "$2" | head -n 1 | cut -d: -f1; }
scope_line="$(line_of 'name: Decide whether the Notification Worker changes' "${WORKFLOW}")"
fallback_line="$(line_of 'name: Decide fallback bootJars' "${WORKFLOW}")"
api_deploy_line="$(line_of 'name: Deploy ECS service' "${WORKFLOW}")"
carry_line="$(line_of 'name: Carry the unchanged Notification Worker into this commit' "${WORKFLOW}")"
record_line="$(line_of 'name: Record immutable dev deployment bundle' "${WORKFLOW}")"
[ -n "${scope_line}" ] && [ "${scope_line}" -lt "${fallback_line}" ] && [ "${scope_line}" -lt "${api_deploy_line}" ] \
  || fail "Worker 교체 판단은 jar 빌드와 서비스 변경 전에 해야 한다."
grep -q 'decide-worker-change.sh' <<< "$(sed -n "${scope_line},${fallback_line}p" "${WORKFLOW}")" \
  || fail "판단은 테스트된 스크립트를 써야 한다."
[ -n "${carry_line}" ] && [ "${carry_line}" -lt "${record_line}" ] || fail "유지한 Worker는 기록 전에 이 커밋에 이어 적어야 한다."
carry_block="$(sed -n "${carry_line},${record_line}p" "${WORKFLOW}")"
grep -q 'copy-image-by-digest.sh' <<< "${carry_block}" || fail "유지한 Worker 이미지에도 이 커밋의 표식을 붙여야 승격·롤백이 받아들인다."
grep -q 'resolve-running-service.sh' <<< "${carry_block}" || fail "기록 직전에 실행 중 Worker가 판단 때와 같은지 다시 확인해야 한다."
[ "$(grep -c "steps.worker_scope.outputs.deploy == 'true'" "${WORKFLOW}")" -eq 5 ] \
  || fail "Worker 빌드 실패 확인·등록·교체·SSM·표식은 교체할 때만 해야 한다."
grep -q 'WORKER_DEPLOY: ${{ steps.worker_scope.outputs.deploy }}' "${WORKFLOW}" || fail "Worker 이미지 빌드도 교체 판단을 따라야 한다."
grep -q 'steps.worker_bundle.outputs.image_uri || steps.worker_carry.outputs.image_uri' "${WORKFLOW}" \
  || fail "기록은 교체한 Worker 또는 유지한 Worker를 담아야 한다."
grep -q "WORKER_UNCHANGED: \${{ steps.worker_carry.outcome == 'success' }}" "${WORKFLOW}" \
  || fail "알림의 unchanged는 실제로 기록에 이어 적었을 때만이어야 한다."
grep -q 'runtime-input.sh read' "${CI_WORKFLOW}" || fail "PR CI는 올린 후보의 label을 다시 읽어 확인해야 한다."
[ "$(grep -c 'labels: org.moimyeon.runtime-input=' "${CI_WORKFLOW}")" -eq 2 ] || fail "두 후보 이미지에 입력 해시 label이 있어야 한다."
grep -q 'worker-scope-test.sh' "${CI_WORKFLOW}" || fail "이 테스트는 CI에서 돌아야 한다."

echo "Worker 선택 교체 계약을 만족한다."
