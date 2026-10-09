#!/usr/bin/env bash

# MOI-512: a task template that Terraform has replaced is deregistered. The
# deploy script must stop with the cause before registering a copy of it, and a
# live promotion must read templates from the Terraform deploy config in SSM,
# not from GitHub variables fixed when the run started.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
DEPLOY_SCRIPT="${ROOT_DIR}/infra/terraform/scripts/deploy-ecs-image.sh"
PROMOTE_WORKFLOW="${ROOT_DIR}/.github/workflows/promote-live.yml"
DEPLOY_CANDIDATES="${ROOT_DIR}/infra/terraform/modules/moimyeon-environment/deploy_candidates.tf"
LIVE_MAIN="${ROOT_DIR}/infra/terraform/envs/live/main.tf"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIR}"' EXIT

fail() {
  echo "ECS 원본 틀 테스트 실패: $1" >&2
  exit 1
}

IMAGE="123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/moimyeon-live-core-api@sha256:$(printf 'a%.0s' {1..64})"

# Fake aws: answers the reads the script makes before registering, records
# every call, and reports the template with FAKE_TEMPLATE_STATUS.
cat > "${TEMP_DIR}/aws" <<FAKE
#!/usr/bin/env bash
echo "\$*" >> "${TEMP_DIR}/calls"
case "\$*" in
  "ecs describe-services"*)
    echo '{"services":[{"deployments":[{"status":"PRIMARY","taskDefinition":"arn:aws:ecs:ap-northeast-2:123456789012:task-definition/moimyeon-live-core-api:7"}]}]}' ;;
  *"task-definition/moimyeon-live-core-api:7"*)
    echo '{"status":"ACTIVE","containerDefinitions":[{"name":"core-api","image":"${IMAGE}"}]}' ;;
  *"task-definition/moimyeon-live-core-api:2"*)
    echo "{\"status\":\"\${FAKE_TEMPLATE_STATUS}\",\"deregisteredAt\":\"2026-10-09\",\"containerDefinitions\":[{\"name\":\"core-api\",\"image\":\"old\"}]}" ;;
  *) exit 99 ;;
esac
FAKE
printf '#!/usr/bin/env bash\nexit 0\n' > "${TEMP_DIR}/docker"
chmod +x "${TEMP_DIR}/aws" "${TEMP_DIR}/docker"

if output="$(env PATH="${TEMP_DIR}:${PATH}" FAKE_TEMPLATE_STATUS=INACTIVE bash "${DEPLOY_SCRIPT}" \
  --cluster moimyeon-live --service core-api --container core-api \
  --template-task-definition "arn:aws:ecs:ap-northeast-2:123456789012:task-definition/moimyeon-live-core-api:2" \
  --image-uri "${IMAGE}" --ssm-parameter /moimyeon/live/core-api/IMAGE_URI 2>&1)"; then
  fail "비활성 원본 틀로 배포를 계속했다."
fi
grep -q 'is not ACTIVE; Terraform has replaced it' <<< "${output}" \
  || fail "비활성 원본 틀이라는 원인을 알리지 않았다: ${output}"
if grep -Eq 'register-task-definition|update-service|put-parameter' "${TEMP_DIR}/calls"; then
  fail "원본 틀을 확인하기 전에 AWS 자원을 바꿨다."
fi

# An ACTIVE template proceeds to registration.
: > "${TEMP_DIR}/calls"
env PATH="${TEMP_DIR}:${PATH}" FAKE_TEMPLATE_STATUS=ACTIVE bash "${DEPLOY_SCRIPT}" \
  --cluster moimyeon-live --service core-api --container core-api \
  --template-task-definition "arn:aws:ecs:ap-northeast-2:123456789012:task-definition/moimyeon-live-core-api:2" \
  --image-uri "${IMAGE}" --ssm-parameter /moimyeon/live/core-api/IMAGE_URI >/dev/null 2>&1 || true
grep -q 'ecs register-task-definition' "${TEMP_DIR}/calls" \
  || fail "ACTIVE 원본 틀인데 등록 단계로 가지 않았다."

# Live promotion reads the templates from the Terraform deploy config.
grep -Fq -- '--template-task-definition "${{ steps.live_config.outputs.ecs_task_definition }}"' "${PROMOTE_WORKFLOW}" \
  || fail "Core API 승격이 Terraform 배포 설정의 원본 틀을 쓰지 않는다."
grep -Fq -- '--template-task-definition "${{ steps.live_config.outputs.worker_ecs_task_definition }}"' "${PROMOTE_WORKFLOW}" \
  || fail "Worker 승격이 Terraform 배포 설정의 원본 틀을 쓰지 않는다."
if grep -Eq 'vars\.MOIMYEON_(WORKER_)?ECS_TASK_DEFINITION_LIVE' "${PROMOTE_WORKFLOW}"; then
  fail "승격이 실행 시작 때 고정되는 GitHub 변수의 원본 틀을 읽는다."
fi
grep -Fq -- '--name "/moimyeon/live/deploy/config"' "${PROMOTE_WORKFLOW}" \
  || fail "승격이 live 배포 설정 파라미터를 읽지 않는다."
grep -Fq 'deploy_config_parameter_name = "/${var.project}/${var.environment}/deploy/config"' "${DEPLOY_CANDIDATES}" \
  || fail "Terraform 배포 설정 파라미터 이름이 승격이 읽는 이름과 다르다."
grep -Eq '^[[:space:]]*publish_deploy_config[[:space:]]*=[[:space:]]*true' "${LIVE_MAIN}" \
  || fail "live가 배포 설정을 SSM에 게시하지 않는다."

echo "deploy-ecs template tests passed"
