#!/usr/bin/env bash
# 하네스 트리거 eval 러너
# 사용: .agents/evals/run.sh trigger <claude|codex> <반복수N> [워킹디렉토리]
# 결과: worklog/MOI-474/evals/trigger-<runtime>-<날짜>.csv + raw/
#
# 감지 규칙: 스킬 메타데이터(이름+description 상시 노출)와 실제 호출을
# 구분하기 위해, 이름 단독이 아니라 "경로/파일 접근 또는 명시 호출" 패턴만
# 트리거로 센다. 스모크 실행에서 raw를 표본 확인해 보정할 것.
set -euo pipefail

MODE="${1:?trigger}" ; RUNTIME="${2:?claude|codex}" ; N="${3:-1}"
ROOT="$(git rev-parse --show-toplevel)"
WORKDIR="${4:-$ROOT}"
TSV="$ROOT/.agents/evals/trigger/requirement-implementation.tsv"
STAMP="$(date +%Y%m%d-%H%M)"
OUT_DIR="$ROOT/worklog/MOI-474/evals"
RAW_DIR="$OUT_DIR/raw/$RUNTIME-$STAMP"
CSV="$OUT_DIR/trigger-$RUNTIME-$STAMP.csv"
mkdir -p "$RAW_DIR"

# 실제 호출 패턴 (2026-08-21 1차 실행에서 보정):
# 경로 문자열 매칭은 git diff --stat 출력 등에 하네스 파일 경로가 찍혀 오탐을
# 낸다. 실제 "호출" 이벤트만 센다. 런타임별로 이벤트 형태가 다르다.
if [ "$RUNTIME" = claude ]; then
  PATTERN='"skill": ?"requirement-implementation"|Launching skill: requirement-implementation'
else
  # codex: 스킬 파일을 실제로 읽는 명령/도구 이벤트 (raw 표본 확인 후 조정)
  PATTERN='requirement-implementation/SKILL\.md'
fi
TIMEOUT_S=240

# macOS에는 GNU timeout이 없다 — perl로 대체
run_with_timeout() { perl -e 'alarm shift; exec @ARGV' "$TIMEOUT_S" "$@"; }

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
    if grep -qE "$PATTERN" "$raw"; then detected=yes; else detected=no; fi
    echo "$STAMP,$RUNTIME,$(version),$id,$expected,$i,$detected,$dur,$raw" >> "$CSV"
    echo "[$id iter$i] expected=$expected detected=$detected (${dur}s)"
  done
done < "$TSV"

echo "결과: $CSV"
echo "주의: raw 표본을 확인해 감지 패턴 오탐/미탐을 보정할 것."
