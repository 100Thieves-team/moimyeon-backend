#!/usr/bin/env bash
# 게이트 자체 테스트 — "걸려야 할 것이 걸리는가"(양성) + "현재 레포는 통과하는가"(음성).
# 시크릿 픽스처는 커밋되면 안 되므로 실행 시점에 임시 생성한다.
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
G=".agents/gates"
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
fail=0

check() { # 이름, 기대exit, 실제exit
  if [ "$3" -eq "$2" ]; then echo "PASS $1"; else echo "FAIL $1 (기대 $2, 실제 $3)"; fail=1; fi
}

# 1. 시크릿: 가짜 AWS 키 → 검출돼야 한다
printf 'val key = "%s%s"\n' "AKIA" "IOSFODNN7EXAMPLE" > "$T/leak.kt"
python3 "$G/scan_secrets.py" --files "$T/leak.kt" > /dev/null; check "secrets-양성" 1 $?
printf 'val key = System.getenv("AWS_KEY")\n' > "$T/clean.kt"
python3 "$G/scan_secrets.py" --files "$T/clean.kt" > /dev/null; check "secrets-음성" 0 $?

# 2. 스킬 lint: name-폴더 불일치 → BLOCKER
mkdir -p "$T/skills/bad-skill"
printf -- '---\nname: wrong-name\ndescription: x\n---\n본문\n' > "$T/skills/bad-skill/SKILL.md"
python3 "$G/lint_skills.py" "$T/skills" > /dev/null; check "lint-양성" 1 $?
python3 "$G/lint_skills.py" .agents/skills > /dev/null; check "lint-실레포" 0 $?

# 3. 프로파일 싱크: dev에만 있는 키 → BLOCK
cat > "$T/app.yml" <<'YML'
spring.application.name: t
---
spring.config.activate.on-profile: dev
feature.x.enabled: true
---
spring.config.activate.on-profile: live
YML
python3 "$G/check_config_profiles.py" "$T/app.yml" > /dev/null; check "profiles-양성" 1 $?
python3 "$G/check_config_profiles.py" > /dev/null; check "profiles-실레포" 0 $?

# 3b. 시크릿 음성 회귀: 실제 레포 소스 전체에 오탐이 없어야 한다
#     (2026-08-25 qa-reviewer가 합성 픽스처만으로는 못 잡는 오탐 2건을 발견)
git ls-files '*.kt' '*.yml' '*.yaml' '*.properties' '*.sql' | grep -v worktrees \
  | xargs python3 "$G/scan_secrets.py" --files > /dev/null 2>&1
check "secrets-실레포-오탐없음" 0 $?

# 4. 페어링: 훅 모드 스모크 (경고는 exit 0)
python3 "$G/check_pairings.py" --staged > /dev/null; check "pairings-smoke" 0 $?

exit $fail
