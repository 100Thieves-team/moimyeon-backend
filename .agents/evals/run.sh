#!/usr/bin/env bash
# 하네스 트리거 eval 러너
# 사용: run.sh trigger claude <N> <격리worktree> <스킬명> <출력경로> <모델>
# 결과: 지정한 출력경로에 trigger-<skill>-<runtime>-<날짜>.csv + raw/
#
# 감지 규칙: 스킬 메타데이터(이름+description 상시 노출)와 실제 호출을
# 구분하기 위해, 이름 단독이 아니라 "경로/파일 접근 또는 명시 호출" 패턴만
# 트리거로 센다. 스모크 실행에서 raw를 표본 확인해 보정할 것.
set -euo pipefail

MODE="${1:?trigger}" ; RUNTIME="${2:?claude}" ; N="${3:-1}"
if [ "$RUNTIME" = codex ]; then
  echo "Codex CLI 평가는 차단한다: read-only 셸 샌드박스는 MCP 외부 쓰기를 격리하지 않는다. 네이티브 읽기 전용 독립 평가를 사용한다." >&2
  exit 2
fi
ROOT="$(git rev-parse --show-toplevel)"
ROOT="$(cd "$ROOT" && pwd -P)"
# 실제 구현 요청을 사용하므로 별도 worktree와 읽기 전용 도구로 평가한다.
WORKDIR="${4:-}"
if [ -n "$WORKDIR" ] && [ -d "$WORKDIR" ]; then
  WORKDIR="$(cd "$WORKDIR" && pwd -P)"
fi
if [ -z "$WORKDIR" ] || [ ! -d "$WORKDIR" ] || [ "$WORKDIR" = "$ROOT" ]; then
  echo "격리 워크트리를 지정하라 (레포 루트 실행 금지):" >&2
  echo "  git worktree add /tmp/eval-wt --detach HEAD" >&2
  echo "  $0 $MODE $RUNTIME $N /tmp/eval-wt <스킬명>" >&2
  exit 2
fi
SKILL="${5:-requirement-implementation}"
OUT_DIR="${6:?작업별 출력경로를 지정한다}"
MODEL="${7:?평가 모델을 명시한다}"
if [ "$MODE" != trigger ] || [ "$RUNTIME" != claude ]; then
  echo "지원 모드: trigger, CLI 런타임: claude" >&2
  exit 2
fi
mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd -P)"
TSV="$ROOT/.agents/evals/trigger/$SKILL.tsv"
STAMP="$(date +%Y%m%d-%H%M%S)-$$"   # 초·PID까지 — 같은 분 재실행이 서로 덮어쓰지 않게
RAW_DIR="$OUT_DIR/raw/$SKILL-$RUNTIME-$STAMP"
CSV="$OUT_DIR/trigger-$SKILL-$RUNTIME-$STAMP.csv"
mkdir -p "$RAW_DIR"

# 실제 호출 패턴 (2026-08-21 1차 실행에서 보정):
# 경로 문자열 매칭은 git diff --stat 출력 등에 하네스 파일 경로가 찍혀 오탐을
# 낸다. 아래는 잠정 표시이며 확정 집계는 score.py의 실제 호출 이벤트로 한다.
PATTERN='"skill": ?"'$SKILL'"|Launching skill: '$SKILL
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
  claude --version 2>/dev/null | head -1
}

echo "run_id,runtime,version,prompt_id,expected,iter,detected,duration_s,raw_file" > "$CSV"
echo "# runtime=$RUNTIME version=$(version) model=$MODEL N=$N date=$STAMP workdir=$WORKDIR timeout=${TIMEOUT_S}s" >> "$CSV"

while IFS=$'\t' read -r id type expected prompt; do
  [ -z "$id" ] && continue
  for i in $(seq 1 "$N"); do
    raw="$RAW_DIR/$id-$i.jsonl"
    start=$(date +%s)
    rc=0
    ( cd "$WORKDIR" && run_with_timeout claude -p "$prompt" \
          --model "$MODEL" --max-turns 4 --restricted \
          --tools 'Read,Glob,Grep,Skill' --allowedTools 'Read,Glob,Grep,Skill' \
          --strict-mcp-config --mcp-config '{"mcpServers":{}}' \
          --no-session-persistence \
          --output-format stream-json --verbose \
          > "$raw" 2>&1 ) || rc=$?
    dur=$(( $(date +%s) - start ))
    # 런타임 실패(미설치·타임아웃·크래시)를 미호출로 세면 거짓 음성이 된다
    if [ ! -s "$raw" ] || [ "$rc" -ne 0 ]; then detected=ERROR
    # 한도·에러 문구는 런타임 업데이트로 바뀐다 — 확정 집계는 score.py
    elif grep -qE "hit your session limit|reached your .{0,30}limit" "$raw"; then detected=LIMIT
    elif grep -qE "$PATTERN" "$raw"; then detected=yes; else detected=no; fi
    echo "$STAMP,$RUNTIME,$(version),$id,$expected,$i,$detected,$dur,$raw" >> "$CSV"
    echo "[$id iter$i] expected=$expected detected=$detected (${dur}s)"
  done
done < "$TSV"

echo "결과: $CSV"
echo "주의: raw 표본을 확인해 감지 패턴 오탐/미탐을 보정할 것."
