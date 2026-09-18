#!/usr/bin/env bash
# L2 커밋 시점 게이트 — .githooks/pre-commit과 CI가 같은 검사를 공유한다 (DR-025).
# 차단: 시크릿 / 스킬 lint BLOCKER / 배포 프로파일 키 누락. 경고: 페어링 3종.
set -uo pipefail
ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"
G=".agents/gates"

staged="$(git diff --cached --name-only --diff-filter=ACMR)"
[ -z "$staged" ] && exit 0

python3 "$G/scan_secrets.py" --staged || exit 1

if echo "$staged" | grep -q "^\.agents/skills/"; then
  python3 "$G/lint_skills.py" || exit 1
fi

python3 "$G/check_pairings.py" --staged   # WARN 전용 — 차단하지 않는다

if echo "$staged" | grep -qE "src/main/resources/application[^/]*\.yml"; then
  python3 "$G/check_config_profiles.py" || exit 1
fi

exit 0
