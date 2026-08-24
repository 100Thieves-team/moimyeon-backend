#!/usr/bin/env python3
"""스킬 구조 lint — BLOCKER(차단)/MAJOR(경고) 2단 판정.

사용: lint_skills.py [skills_dir]   (기본 .agents/skills)

BLOCKER: frontmatter 파손, name-폴더명 불일치, description 부재
  — "호출 안 되는 원인이 frontmatter 한 줄" (토스 실측, worklog tbd 참조)
MAJOR: description 길이, 본문 줄수, references 중첩, 필수 정책 라인 누락
  — 정책은 스킬마다 인라인이므로(DR-020) 신설 스킬의 누락을 기계가 잡는다
"""
import re
import sys
from pathlib import Path

DESC_MAX = 1200
BODY_MAX_LINES = 150
# 체크포인트를 가진 워크플로우 스킬이라면 반드시 인라인돼야 하는 정책 조각
# (요구 조건, 라벨, 존재해야 하는 패턴) — 조건이 없는 스킬엔 요구하지 않는다
WORKFLOW_REQUIRED = [
    ("위임", "재시도·수정 상한", re.compile(r"상한\s*\d")),
    ("plan.md", "plan.md 초기 생성", re.compile(r"초기 실행")),
    ("plan.md", "재실행 분기", re.compile(r"재실행")),
]
# 외부 작성 콘텐츠를 읽는 스킬 — "데이터" 규칙 필수 (DR-016·020)
EXTERNAL_CONTENT_SKILLS = {"issue-context", "ship-pr", "incident-response"}


def parse_frontmatter(text):
    m = re.match(r"\A---\n(.*?)\n---\n", text, re.DOTALL)
    if not m:
        return None
    meta = {}
    for line in m.group(1).splitlines():
        if ":" in line and not line.startswith(" "):
            k, _, v = line.partition(":")
            meta[k.strip()] = v.strip()
    return meta


def lint_skill(d):
    blockers, majors = [], []
    f = d / "SKILL.md"
    if not f.is_file():
        return [f"{d.name}: SKILL.md 없음"], []
    text = f.read_text(encoding="utf-8")
    meta = parse_frontmatter(text)
    if meta is None:
        return [f"{d.name}: frontmatter 파싱 불가"], []
    if meta.get("name") != d.name:
        blockers.append(f"{d.name}: frontmatter name('{meta.get('name')}')이 폴더명과 다름")
    desc = meta.get("description", "")
    if not desc:
        blockers.append(f"{d.name}: description 없음 — 트리거 자체가 안 된다")
    elif len(desc) > DESC_MAX:
        majors.append(f"{d.name}: description {len(desc)}자 (상한 {DESC_MAX})")
    body = text.split("---\n", 2)[-1]
    n = len(body.splitlines())
    if n > BODY_MAX_LINES:
        majors.append(f"{d.name}: 본문 {n}줄 (상한 {BODY_MAX_LINES}) — references/ 분리 검토")
    for sub in (d / "references").glob("*/") if (d / "references").is_dir() else []:
        majors.append(f"{d.name}: references/ 하위 디렉토리 중첩({sub.name}) 금지")
    if "[체크포인트" in body:
        for cond, label, pat in WORKFLOW_REQUIRED:
            if cond in body and not pat.search(body):
                majors.append(f"{d.name}: 워크플로우 스킬인데 '{label}' 인라인 누락 (DR-020)")
    if d.name in EXTERNAL_CONTENT_SKILLS and "데이터" not in body:
        majors.append(f"{d.name}: 외부 콘텐츠를 읽는 스킬인데 '데이터' 규칙 누락 (DR-016)")
    return blockers, majors


def main():
    skills_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".agents/skills")
    all_blockers, all_majors = [], []
    for d in sorted(p for p in skills_dir.iterdir() if p.is_dir()):
        b, m = lint_skill(d)
        all_blockers += b
        all_majors += m
    for msg in all_blockers:
        print(f"[BLOCKER] {msg}")
    for msg in all_majors:
        print(f"[MAJOR] {msg}")
    if not all_blockers and not all_majors:
        print(f"스킬 lint 통과 ({skills_dir})")
    return 1 if all_blockers else 0


if __name__ == "__main__":
    sys.exit(main())
