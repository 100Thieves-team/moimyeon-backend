#!/usr/bin/env bash
# 하네스 트리거 eval 러너
# 사용: .agents/evals/run.sh trigger <claude|codex> <반복수N> [워킹디렉토리] [스킬명]
# 결과: .worklog/MOI-474/evals/trigger-<runtime>-<날짜>.csv + raw/
#
# 감지 규칙: 스킬 메타데이터(이름+description 상시 노출)와 실제 호출을
# 구분하기 위해, 이름 단독이 아니라 "경로/파일 접근 또는 명시 호출" 패턴만
# 트리거로 센다. 스모크 실행에서 raw를 표본 확인해 보정할 것.
set -euo pipefail

MODE="${1:?trigger}" ; RUNTIME="${2:?claude|codex}" ; N="${3:-1}"
ROOT="$(git rev-parse --show-toplevel)"
# 트리거 프롬프트는 "구현해줘" 류 실행 요청이고 claude 경로는 권한을 우회한다.
# 레포 루트에서 돌면 측정 1회가 워킹트리를 오염시킨다 — 격리 워크트리를 강제한다.
WORKDIR="${4:-}"
if [ -z "$WORKDIR" ] || [ "$WORKDIR" = "$ROOT" ]; then
  echo "격리 워크트리를 지정하라 (레포 루트 실행 금지):" >&2
  echo "  git worktree add /tmp/eval-wt --detach HEAD" >&2
  echo "  $0 $MODE $RUNTIME $N /tmp/eval-wt <스킬명>" >&2
  exit 2
fi
SKILL="${5:-requirement-implementation}"
TSV="$ROOT/.agents/evals/trigger/$SKILL.tsv"
STAMP="$(date +%Y%m%d-%H%M)"
OUT_DIR="$ROOT/.worklog/MOI-474/evals"
RAW_DIR="$OUT_DIR/raw/$SKILL-$RUNTIME-$STAMP"
CSV="$OUT_DIR/trigger-$SKILL-$RUNTIME-$STAMP.csv"
mkdir -p "$RAW_DIR"

# 실제 호출 패턴 (2026-08-21 1차 실행에서 보정):
# 경로 문자열 매칭은 git diff --stat 출력 등에 하네스 파일 경로가 찍혀 오탐을
# 낸다. 실제 "호출" 이벤트만 센다. 런타임별로 이벤트 형태가 다르다.
if [ "$RUNTIME" = claude ]; then
  PATTERN='"skill": ?"'$SKILL'"|Launching skill: '$SKILL
else
  # codex: 스킬 파일을 실제로 읽는 명령 이벤트 — 확정 집계는 score.py로
  PATTERN="skills/$SKILL"
fi
TIMEOUT_S=240

# macOS에는 GNU timeout이 없고, perl alarm은 SIGALRM을 무시하는 프로세스
# (codex에서 92분 폭주 관찰, 2026-08-21)를 못 죽인다 — kill 워치독 사용
run_with_timeout() {
  "$@" &
  local pid=$!
  ( sleep "$TIMEOUT_S"; kill -9 "$pid" 2>/dev/null ) &
  local wpid=$!
  wait "$pid" 2>/dev/null
  local rc=$?
  kill "$wpid" 2>/dev/null
  return $rc
}

version() {
  if [ "$RUNTIME" = claude ]; then claude --version 2>/dev/null | head -1
  else codex --version 2>/dev/null | head -1; fi
}

echo "run_id,runtime,version,prompt_id,expected,iter,detected,duration_s,raw_file" > "$CSV"
echo "# runtime=$RUNTIME version=$(version) N=$N date=$STAMP workdir=$WORKDIR timeout=${TIMEOUT_S}s" >> "$CSV"

while IFS=$'\t' read -r id type expected prompt; do
  [ -z "$id" ] && continue
  for i in $(seq 1 "$N"); do
    raw="$RAW_DIR/$id-$i.jsonl"
    start=$(date +%s)
    if [ "$RUNTIME" = claude ]; then
      ( cd "$WORKDIR" && run_with_timeout claude -p "$prompt" \
          --max-turns 4 --permission-mode bypassPermissions \
          --output-format stream-json --verbose \
          > "$raw" 2>&1 ) || true
    else
      ( cd "$WORKDIR" && run_with_timeout codex exec --json \
          --sandbox read-only "$prompt" < /dev/null > "$raw" 2>&1 ) || true
    fi
    dur=$(( $(date +%s) - start ))
    # 한도·에러 문구는 런타임 업데이트로 바뀐다 — 확정 집계는 score.py(is_error 기반)
    if grep -qE "hit your session limit|reached your .{0,30}limit" "$raw"; then detected=LIMIT
    elif grep -qE "$PATTERN" "$raw"; then detected=yes; else detected=no; fi
    echo "$STAMP,$RUNTIME,$(version),$id,$expected,$i,$detected,$dur,$raw" >> "$CSV"
    echo "[$id iter$i] expected=$expected detected=$detected (${dur}s)"
  done
done < "$TSV"

echo "결과: $CSV"
echo "주의: raw 표본을 확인해 감지 패턴 오탐/미탐을 보정할 것."
