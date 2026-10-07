#!/usr/bin/env bash

# MOI-565: dev push deploys prove PR CI verified the merged tree and wait only
# for unapplied Terraform changes. Exercises both scripts against a real Git
# history with fake gh/aws responses.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
VERIFY="${ROOT_DIR}/infra/terraform/scripts/verify-pr-ci.sh"
WAITER="${ROOT_DIR}/infra/terraform/scripts/wait-for-terraform-boundary.sh"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIR}"' EXIT

fail() {
  echo "배포 소스 검증 계약 위반: $1" >&2
  exit 1
}

mkdir -p "${TEMP_DIR}/bin"
# Fake gh returns raw API documents and applies --jq with real jq, so the
# script's own selection filters are exercised. Like API version 2026-03-10,
# PRs carry merge_commit_sha: null.
cat > "${TEMP_DIR}/bin/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
path=""
filter="."
while [ "$#" -gt 0 ]; do
  case "$1" in
    --jq) filter="$2"; shift 2 ;;
    -H|-f|--method) shift 2 ;;
    /repos/*) path="$1"; shift ;;
    *) shift ;;
  esac
done
case "${path}" in
  */commits/*/pulls|*/pulls)
    [ "${FAKE_PR_API:-ok}" = ok ] || { echo "HTTP 502: Bad Gateway" >&2; exit 1; }
    if [ "${path}" != "${path%/commits/*}" ] && [ "${FAKE_PR_INDEX:-ready}" = lagging ]; then
      document='[]'
    elif [ "${path}" = "${path%/commits/*}" ] && [ "${FAKE_PR_INDEX:-ready}" != lagging ]; then
      document='[]'
    elif [ -n "${FAKE_PR_DELAY_FILE:-}" ] && [ ! -e "${FAKE_PR_DELAY_FILE}" ]; then
      : > "${FAKE_PR_DELAY_FILE}"
      document='[]'
    elif [ "${FAKE_PR_MODE}" = none ]; then
      document='[]'
    else
      document="$(jq -cn --arg merge "${FAKE_MERGE_SHA}" --arg head "${FAKE_HEAD_SHA}" --arg base "${FAKE_BASE:-dev}" \
        '[{number:7,merge_commit_sha:null,merged_at:null,base:{ref:$base},head:{sha:$head}},
          {number:8,merge_commit_sha:null,merged_at:"2026-10-06T00:00:00Z",base:{ref:$base},head:{sha:$head}}]')"
    fi
    ;;
  */git/commits/*)
    document="$(jq -cn --arg tree "$(git rev-parse "${path##*/}^{tree}")" '{tree:{sha:$tree}}')"
    ;;
  */check-runs)
    document="$(jq -cn --arg conclusion "${FAKE_CONCLUSION}" '{check_runs:[
      {id:41,app:{slug:"other-ci"},started_at:"2026-10-06T00:09:00Z",conclusion:"success"},
      {id:42,app:{slug:"github-actions"},status:"completed",started_at:"2026-10-06T00:00:00Z",conclusion:"failure"},
      {id:43,app:{slug:"github-actions"},status:"completed",started_at:"2026-10-06T00:05:00Z",conclusion:(if $conclusion == "" then null else $conclusion end)},
      {id:44,app:{slug:"github-actions"},status:"in_progress",started_at:"2026-10-06T00:07:00Z",conclusion:null}]}')"
    ;;
  */actions/jobs/43)
    [ "${FAKE_ACTIONS_API:-ok}" = ok ] || { echo "HTTP 403: Resource not accessible by integration" >&2; exit 1; }
    document="$(jq -cn --arg head "${FAKE_JOB_HEAD_SHA:-${FAKE_HEAD_SHA}}" '{run_id:900,run_attempt:2,head_sha:$head,started_at:"2026-10-06T00:04:00Z"}')"
    ;;
  */actions/runs/900/attempts/2/jobs)
    document="$(jq -cn --arg conclusion "${FAKE_IMAGE_CONCLUSION:-success}" \
      '{jobs:[{name:"build",conclusion:"success"},{name:"image",conclusion:$conclusion}]}')"
    ;;
  *) exit 1 ;;
esac
jq -r "${filter}" <<< "${document}"
EOF
cat > "${TEMP_DIR}/bin/aws" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
[ -n "${FAKE_APPLIED_SHA:-}" ] || exit 255
printf '%s\n' "${FAKE_APPLIED_SHA}"
EOF
chmod +x "${TEMP_DIR}/bin/gh" "${TEMP_DIR}/bin/aws"
export PATH="${TEMP_DIR}/bin:${PATH}"
export GITHUB_REPOSITORY="100Thieves-team/moimyeon-backend"
export VERIFY_PR_CI_RETRY_SECONDS=0

repo="${TEMP_DIR}/repo"
git init -q "${repo}"
cd "${repo}"
git config user.email test@example.test
git config user.name test
commit() {
  mkdir -p "$(dirname "$1")"
  echo "$2" > "$1"
  git add -A
  git commit -qm "$2"
  git rev-parse HEAD
}

base="$(commit infra/terraform/envs/dev/main.tf base)"
applied="$(commit core/app.kt app-1)"
app_only="$(commit core/app.kt app-2)"
infra_test_only="$(commit infra/terraform/tests/check.sh test-only)"
infra_change="$(commit infra/terraform/envs/dev/main.tf infra-2)"
after_infra="$(commit core/app.kt app-3)"

# The PR head carries the same tree as the merge commit when branches are up to date.
git checkout -q -b pr "${applied}"
pr_head="$(commit core/app.kt app-2)"
git checkout -q -b stale "${applied}"
stale_head="$(commit core/app.kt app-stale)"
git checkout -q --detach "${after_infra}"

# --- verify-pr-ci.sh ---------------------------------------------------------
verify_ok() {
  env "$@" bash "${VERIFY}" "${app_only}" dev 2>/dev/null
}
result="$(verify_ok FAKE_PR_MODE=one FAKE_MERGE_SHA="${app_only}" FAKE_HEAD_SHA="${pr_head}" FAKE_CONCLUSION=success)" \
  || fail "PR CI가 같은 트리를 통과했으면 배포해야 한다."
tree="$(git rev-parse "${app_only}^{tree}")"
grep -qx "tree=${tree}" <<< "${result}" || fail "검증된 머지 트리를 출력해야 한다."
grep -qx "candidate_tag=tree-${tree}-run-900-2" <<< "${result}" \
  || fail "같은 CI 실행 시도의 image job이 성공하면 그 실행의 후보 태그를 써야 한다."
grep -qx "candidate_not_before=2026-10-06T00:04:00Z" <<< "${result}" \
  || fail "후보는 그 CI 실행이 시작된 뒤 올라간 이미지만 인정해야 한다."

result="$(verify_ok FAKE_PR_MODE=one FAKE_MERGE_SHA="${app_only}" FAKE_HEAD_SHA="${pr_head}" FAKE_CONCLUSION=success \
  FAKE_ACTIONS_API=forbidden)" || fail "Actions API 실패는 배포를 막지 않고 빌드로 넘어가야 한다."
grep -qx "candidate_tag=" <<< "${result}" || fail "Actions API 실패 시 후보를 쓰면 안 된다."

result="$(verify_ok FAKE_PR_INDEX=lagging FAKE_PR_MODE=one FAKE_MERGE_SHA="${app_only}" FAKE_HEAD_SHA="${pr_head}" \
  FAKE_CONCLUSION=success)" || fail "커밋-PR 연결이 늦으면 최근 머지된 PR 목록에서 찾아야 한다."
grep -qx "tree=${tree}" <<< "${result}" || fail "PR 목록으로 찾은 경우에도 검증한 트리를 출력해야 한다."

result="$(verify_ok VERIFY_PR_CI_RETRY_SECONDS=0 FAKE_PR_DELAY_FILE="${TEMP_DIR}/pr-delay" FAKE_PR_MODE=one \
  FAKE_MERGE_SHA="${app_only}" FAKE_HEAD_SHA="${pr_head}" FAKE_CONCLUSION=success)" \
  || fail "머지 PR 연결이 늦게 보여도 재시도로 찾아야 한다."

result="$(verify_ok FAKE_PR_MODE=one FAKE_MERGE_SHA="${app_only}" FAKE_HEAD_SHA="${pr_head}" FAKE_CONCLUSION=success \
  FAKE_IMAGE_CONCLUSION=failure)" || fail "image job 실패는 배포를 막지 않고 빌드로 넘어가야 한다."
grep -qx "candidate_tag=" <<< "${result}" || fail "image job이 실패한 실행의 후보는 쓰면 안 된다."

result="$(verify_ok FAKE_PR_MODE=one FAKE_MERGE_SHA="${app_only}" FAKE_HEAD_SHA="${pr_head}" FAKE_CONCLUSION=success \
  FAKE_JOB_HEAD_SHA="${stale_head}")" || fail "다른 head의 실행은 후보만 버려야 한다."
grep -qx "candidate_tag=" <<< "${result}" || fail "검증한 head가 아닌 실행의 후보는 쓰면 안 된다."

expect_verify_failure() {
  local reason="$1"
  shift
  if env "$@" bash "${VERIFY}" "${app_only}" dev >/dev/null 2>&1; then
    fail "${reason}"
  fi
}
if errors="$(env FAKE_PR_API=fail FAKE_PR_MODE=one FAKE_MERGE_SHA="${app_only}" FAKE_HEAD_SHA="${pr_head}" \
  FAKE_CONCLUSION=success VERIFY_PR_CI_ATTEMPTS=2 bash "${VERIFY}" "${app_only}" dev 2>&1 >/dev/null)"; then
  fail "PR 조회 API가 계속 실패하면 배포를 막아야 한다."
fi
grep -q 'GitHub API call failed' <<< "${errors}" || fail "API 실패는 'PR 없음'과 구분되게 기록해야 한다."

expect_verify_failure "트리가 다른 PR head(branch update 누락)는 거부해야 한다." \
  FAKE_PR_MODE=one FAKE_MERGE_SHA="${app_only}" FAKE_HEAD_SHA="${stale_head}" FAKE_CONCLUSION=success
expect_verify_failure "build 실패는 거부해야 한다." \
  FAKE_PR_MODE=one FAKE_MERGE_SHA="${app_only}" FAKE_HEAD_SHA="${pr_head}" FAKE_CONCLUSION=failure
expect_verify_failure "build 결과가 없으면 거부해야 한다." \
  FAKE_PR_MODE=one FAKE_MERGE_SHA="${app_only}" FAKE_HEAD_SHA="${pr_head}" FAKE_CONCLUSION=
expect_verify_failure "머지 PR이 없는 커밋은 거부해야 한다." \
  FAKE_PR_MODE=none FAKE_CONCLUSION=success
expect_verify_failure "dev가 아닌 브랜치로 머지된 PR은 거부해야 한다." \
  FAKE_PR_MODE=one FAKE_BASE=main FAKE_MERGE_SHA="${app_only}" FAKE_HEAD_SHA="${pr_head}" FAKE_CONCLUSION=success

# --- wait-for-terraform-boundary.sh -----------------------------------------
export TERRAFORM_BOUNDARY_WAIT_SECONDS=1 TERRAFORM_BOUNDARY_POLL_SECONDS=1
parameter=/moimyeon/dev/deploy/terraform-applied-sha

FAKE_APPLIED_SHA="${applied}" bash "${WAITER}" "${app_only}" "${parameter}" >/dev/null \
  || fail "앱 전용 커밋은 Terraform을 기다리지 않아야 한다."
FAKE_APPLIED_SHA="${applied}" bash "${WAITER}" "${infra_test_only}" "${parameter}" >/dev/null \
  || fail "Terraform 계약 테스트만 바뀐 커밋은 기다리지 않아야 한다."
FAKE_APPLIED_SHA="${infra_change}" bash "${WAITER}" "${after_infra}" "${parameter}" >/dev/null \
  || fail "적용된 인프라 뒤의 앱 커밋은 바로 배포해야 한다."
FAKE_APPLIED_SHA="${after_infra}" bash "${WAITER}" "${app_only}" "${parameter}" >/dev/null \
  || fail "더 새로운 인프라가 이미 적용됐으면 기다리지 않아야 한다."

if FAKE_APPLIED_SHA="${applied}" bash "${WAITER}" "${after_infra}" "${parameter}" >/dev/null 2>&1; then
  fail "적용되지 않은 인프라 변경 뒤의 커밋은 기다려야 한다."
fi
if FAKE_APPLIED_SHA= bash "${WAITER}" "${app_only}" "${parameter}" >/dev/null 2>&1; then
  fail "적용 기록이 없으면 기다려야 한다."
fi
if FAKE_APPLIED_SHA="${base}" bash "${WAITER}" "not-a-sha" "${parameter}" >/dev/null 2>&1; then
  fail "잘못된 SHA 입력은 거부해야 한다."
fi

echo "배포 소스 검증 계약을 만족한다."
