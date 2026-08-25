#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIR}"' EXIT

cat > "${TEMP_DIR}/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
title="Terraform Apply ${FAKE_BRANCH}@${FAKE_SHA}"
case "${FAKE_MODE}" in
  success)
    jq -cn --arg title "${title}" '{workflow_runs:[{id:1,display_title:$title,status:"completed",conclusion:"success",html_url:"https://example.test/success"}]}'
    ;;
  failure)
    jq -cn --arg title "${title}" '{workflow_runs:[{id:1,display_title:$title,status:"completed",conclusion:"failure",html_url:"https://example.test/failure"}]}'
    ;;
  missing)
    printf '%s\n' '{"workflow_runs":[]}'
    ;;
  *) exit 1 ;;
esac
EOF
chmod +x "${TEMP_DIR}/gh"

export PATH="${TEMP_DIR}:${PATH}"
export GITHUB_REPOSITORY="100Thieves-team/moimyeon-backend"
export FAKE_BRANCH="dev"
export FAKE_SHA="0123456789abcdef0123456789abcdef01234567"

FAKE_MODE=success \
  bash "${ROOT_DIR}/infra/terraform/scripts/wait-for-terraform-apply.sh" \
    "${FAKE_BRANCH}" "${FAKE_SHA}" >/dev/null

if FAKE_MODE=failure \
  bash "${ROOT_DIR}/infra/terraform/scripts/wait-for-terraform-apply.sh" \
    "${FAKE_BRANCH}" "${FAKE_SHA}" >/dev/null 2>&1; then
  echo "Failed Terraform boundary must fail the waiter." >&2
  exit 1
fi

if TERRAFORM_APPLY_WAIT_SECONDS=1 TERRAFORM_APPLY_POLL_SECONDS=1 FAKE_MODE=missing \
  bash "${ROOT_DIR}/infra/terraform/scripts/wait-for-terraform-apply.sh" \
    "${FAKE_BRANCH}" "${FAKE_SHA}" >/dev/null 2>&1; then
  echo "Missing Terraform boundary must time out." >&2
  exit 1
fi

echo "Terraform Apply boundary waiter contract passed."
