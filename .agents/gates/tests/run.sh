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

# 3a. 시크릿 우회 회귀: 형태 기반 예외로 실제 시크릿이 새면 안 된다
#     (2026-08-25 Security Sentinel이 CONSTANT_CASE 예외의 우회를 잡아냈다)
printf 'val secret = "QK7XTPLM4NDVRWS9"\n' > "$T/bypass.kt"  # gate:allow-secret (픽스처)
python3 "$G/scan_secrets.py" --files "$T/bypass.kt" > /dev/null; check "secrets-우회차단" 1 $?
printf 'const val ACCESS_TOKEN = "ACCESS_TOKEN"\n' > "$T/keyname.kt"
python3 "$G/scan_secrets.py" --files "$T/keyname.kt" > /dev/null; check "secrets-키이름반복-제외" 0 $?
printf 'password: SuperSecretPwd123 # 주석\n' > "$T/inline.yml"  # gate:allow-secret (픽스처)
python3 "$G/scan_secrets.py" --files "$T/inline.yml" > /dev/null; check "secrets-인라인주석" 1 $?
# 줄 어딘가의 ${...} 를 덧붙여 예외를 얻는 우회 (2026-08-25 Security Sentinel)
printf 'password: MyRealSecretPass1234 # default is ${SOME_VAR}\n' > "$T/ph.yml"  # gate:allow-secret (픽스처)
python3 "$G/scan_secrets.py" --files "$T/ph.yml" > /dev/null; check "secrets-placeholder-우회차단" 1 $?
# placeholder **안쪽** 키 이름은 값이 아니라 참조다 — 실레포 db-core.yml 형태
printf 'password: ${storage.database.core-db.password:moimyeon}\n' > "$T/ph-ok.yml"
python3 "$G/scan_secrets.py" --files "$T/ph-ok.yml" > /dev/null; check "secrets-placeholder-내부-제외" 0 $?

# 3b. 시크릿 음성 회귀: 실제 레포 소스 전체에 오탐이 없어야 한다
#     (2026-08-25 qa-reviewer가 합성 픽스처만으로는 못 잡는 오탐 2건을 발견)
git ls-files '*.kt' '*.yml' '*.yaml' '*.properties' '*.sql' | grep -v worktrees \
  | xargs python3 "$G/scan_secrets.py" --files > /dev/null 2>&1
check "secrets-실레포-오탐없음" 0 $?

# 4. 페어링: 훅 모드 스모크 (경고는 exit 0)
python3 "$G/check_pairings.py" --staged > /dev/null; check "pairings-smoke" 0 $?

# 5. Gitleaks CI 계약: 변경 범위를 공유하고, 새 ref는 HEAD 이력을 전수 검사한다
ci=".github/workflows/ci.yml"
checkout_pin='uses: actions/checkout@11d5960a326750d5838078e36cf38b85af677262'
checkout_pin_count="$(grep -Fc -- "$checkout_pin" "$ci")"
grep -Fq -- 'name: Gitleaks' "$ci"; check "gitleaks-step" 0 $?
grep -Fq -- '--config=/repo/.gitleaks.toml' "$ci"; check "gitleaks-config-path" 0 $?
grep -Fq -- '--gitleaks-ignore-path=/repo/.gitleaksignore' "$ci"; check "gitleaks-ignore-path" 0 $?
grep -Fq -- 'detect --source=/repo --log-opts="${GATE_RANGE}"' "$ci"; check "gitleaks-range" 0 $?
grep -Fq -- 'detect --source=/repo --log-opts=HEAD' "$ci"; check "gitleaks-new-ref-head" 0 $?
[ "$checkout_pin_count" -eq 2 ]; check "gitleaks-checkout-pin" 0 $?
grep -Fq -- 'uses: actions/checkout@v4' "$ci"; check "gitleaks-no-mutable-checkout" 1 $?
grep -Fq -- 'name: Scan repository secrets' "$ci"; check "gitleaks-no-old-step" 1 $?

exit $fail
