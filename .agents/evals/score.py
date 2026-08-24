#!/usr/bin/env python3
"""트리거 eval raw 재집계 — 실제 '호출'만 센다.

사용: score.py <raw_dir> <skill_name>
raw_dir 이름에 claude/codex가 포함돼야 런타임을 판별한다.

경로 문자열 grep은 오탐을 낸다 (2026-08-21 1차 실행에서 확인):
- git diff --stat 출력에 하네스 파일 경로가 등장
- 레포에 커밋된 eval 문서 자체를 에이전트가 읽음
- 병렬 실행 중인 다른 eval의 raw 파일을 읽음 (파일 내용이 raw에 인용됨)
따라서 에이전트 "자신의 행동" 이벤트만 파싱한다:
- claude(stream-json): Skill 도구 호출 이벤트 (톱레벨 JSON 구조라 인용된
  파일 내용의 이스케이프된 문자열과 구분됨)
- codex(--json): command_execution의 command 필드 (스킬 파일을 실제로 읽는
  명령), agent_message/todo/reasoning의 언급은 보조 신호
"""
import json
import re
import sys
from pathlib import Path

raw_dir = Path(sys.argv[1])
skill = sys.argv[2]
runtime = "claude" if "claude" in raw_dir.name else "codex"

claude_pat = re.compile(r'"skill": ?"%s"|Launching skill: %s' % (re.escape(skill), re.escape(skill)))


# 실행 실패(사용량 한도 등)는 미호출과 구분해야 한다. 문구는 런타임 업데이트로
# 바뀌므로("hit your session limit" → "reached your Fable 5 limit", 2026-08-25에
# 16건 오집계) 문구가 아니라 result 이벤트의 is_error를 1차 신호로 쓴다.
LIMIT_TEXT = re.compile(r"hit your session limit|reached your .{0,30}limit|usage limit")


def score_claude(path: Path) -> str:
    text = path.read_text(encoding="utf-8", errors="replace")
    invoked = bool(claude_pat.search(text))
    # 호출 후 한도에 걸린 것은 트리거가 관측된 것이므로 INVOKED가 맞다.
    # 호출 전에 한도로 죽었으면 미호출이 아니라 측정 불가(LIMIT)다.
    # result의 is_error는 max-turns 소진으로도 켜지므로 한도 신호로 쓰지 않는다.
    if invoked:
        return "INVOKED"
    return "LIMIT" if LIMIT_TEXT.search(text) else "no"


def score_codex(path: Path) -> str:
    cmd_read = mention = False
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if not line.startswith("{"):
            continue
        try:
            ev = json.loads(line)
        except ValueError:
            continue
        item = ev.get("item") or {}
        t = item.get("type")
        if t == "command_execution" and f"skills/{skill}" in item.get("command", ""):
            cmd_read = True
        elif t in ("agent_message", "reasoning") and skill in item.get("text", ""):
            mention = True
        elif t == "todo_list":
            if any(skill in it.get("text", "") for it in item.get("items", [])):
                mention = True
    return "INVOKED" if cmd_read else ("mentioned" if mention else "no")


score = score_claude if runtime == "claude" else score_codex
for f in sorted(raw_dir.glob("*.jsonl")):
    print(f"{f.stem}\t{score(f)}")
