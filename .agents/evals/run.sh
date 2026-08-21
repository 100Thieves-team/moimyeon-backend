#!/usr/bin/env bash
# 하네스 트리거 eval 러너 (초안 — 첫 실행에서 감지 휴리스틱 보정)
# 사용: .agents/evals/run.sh trigger <claude|codex> <반복수N>
# 결과: worklog/MOI-474/evals/trigger-<runtime>-<날짜>.csv + raw/
set -euo pipefail

MODE="${1:?trigger}" ; RUNTIME="${2:?claude|codex}" ; N="${3:-1}"
ROOT="$(git rev-parse --show-toplevel)"
TSV="$ROOT/.agents/evals/trigger/requirement-implementation.tsv"
STAMP="$(date +%Y%m%d-%H%M)"
OUT_DIR="$ROOT/worklog/MOI-474/evals"
RAW_DIR="$OUT_DIR/raw/$RUNTIME-$STAMP"
CSV="$OUT_DIR/trigger-$RUNTIME-$STAMP.csv"
mkdir -p "$RAW_DIR"

# 감지 휴리스틱: 응답·트랜스크립트에 대상 스킬 경로/이름이 등장하면 트리거로 간주
SKILL_PATTERN='requirement-implementation'

version() {
  if [ "$RUNTIME" = claude ]; then claude --version 2>/dev/null | head -1
  else codex --version 2>/dev/null | head -1; fi
}

echo "run_id,runtime,version,prompt_id,expected,iter,detected,duration_s,raw_file" > "$CSV"
echo "# runtime=$RUNTIME version=$(version) N=$N date=$STAMP" >> "$CSV"

while IFS=$'\t' read -r id type expected prompt; do
  [ -z "$id" ] && continue
  for i in $(seq 1 "$N"); do
    raw="$RAW_DIR/$id-$i.txt"
    start=$(date +%s)
    if [ "$RUNTIME" = claude ]; then
      # 읽기 전용·짧은 턴: 트리거 여부만 관찰
      claude -p "$prompt" --max-turns 4 --output-format json \
        > "$raw" 2>&1 || true
    else
      codex exec --sandbox read-only "$prompt" > "$raw" 2>&1 || true
    fi
    dur=$(( $(date +%s) - start ))
    if grep -q "$SKILL_PATTERN" "$raw"; then detected=yes; else detected=no; fi
    echo "$STAMP,$RUNTIME,$(version),$id,$expected,$i,$detected,$dur,$raw" >> "$CSV"
    echo "[$id iter$i] expected=$expected detected=$detected (${dur}s)"
  done
done < "$TSV"

echo "결과: $CSV"
echo "주의: raw 트랜스크립트를 표본 확인해 감지 휴리스틱 오탐/미탐을 보정할 것."
