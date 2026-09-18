#!/usr/bin/env python3
"""조건부 정합성 게이트 (WARN) — "A를 고쳤으면 B도 따라와야 정상이다".

사용:
  check_pairings.py --staged           # 커밋 예정 파일 기준 (pre-commit)
  check_pairings.py --worktree p ...   # 지정 파일 기준, 짝은 작업 트리 전체에서 찾음 (훅)

경고만 낸다(exit 0). 정밀 판정은 리뷰어(확률 층)의 몫 — 이 게이트는
"잊었을 가능성"을 커밋 전에 알려주는 것까지다 (DR-025).
"""
import re
import subprocess
import sys
from pathlib import Path

DEP_FILES = re.compile(r"(^|/)(build\.gradle\.kts|settings\.gradle\.kts|gradle\.properties)$")
TEST_FILE = re.compile(r"src/test/.*\.kt$")

RULES = [
    {
        "id": "entity-schema",
        # storage 메인 코드 중 매핑 어노테이션이 있는 파일만 (메서드-only 수정 오탐 축소)
        "trigger": lambda p, content: (
            p.startswith("storage/db-core/src/main/") and p.endswith(".kt")
            and content is not None and re.search(r"@(Entity|Table|Column)\b", content)
        ),
        "required": re.compile(r"(db/migration/|storage/db-core/src/main/resources/schema\.sql)"),
        "msg": "엔티티 매핑 변경인데 Flyway 마이그레이션·schema.sql 변경이 없다 — 스키마 영향이 있으면 storage.md의 Flyway-only 규칙을 따른다",
    },
    {
        "id": "api-docs",
        "trigger": lambda p, content: (
            "/src/main/" in p and re.search(r"(Controller|Request|Response)\.kt$", p) is not None
        ),
        "required": TEST_FILE,
        "msg": "API 계약(Controller·DTO) 변경인데 테스트 변경이 없다 — RestDocs 문서 테스트가 계약을 따라가야 한다 (api-docs.md)",
    },
    {
        "id": "deps-regression",
        "trigger": lambda p, content: DEP_FILES.search(p) is not None,
        "required": TEST_FILE,
        "msg": "의존성 변경인데 테스트 변경이 없다 — 회귀 검증 근거(테스트 또는 PR 본문의 검증 절)를 남긴다 (qa-review.md 위험 신호)",
    },
]


def git_lines(*args):
    out = subprocess.run(["git", *args], capture_output=True, text=True).stdout
    return [line for line in out.splitlines() if line.strip()]


def staged_content(path):
    r = subprocess.run(["git", "show", f":{path}"], capture_output=True, text=True)
    return r.stdout if r.returncode == 0 else None


def worktree_changed():
    files = set()
    for line in git_lines("status", "--porcelain"):
        files.add(line[3:].split(" -> ")[-1])
    return files


def check(subjects, changed, read_content):
    warns = []
    for path in subjects:
        for rule in RULES:
            content = read_content(path) if path.endswith((".kt", ".kts", ".properties")) else None
            if rule["trigger"](path, content) and not any(rule["required"].search(c) for c in changed):
                warns.append((rule["id"], path, rule["msg"]))
    return warns


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "--staged"
    if mode == "--staged":
        subjects = git_lines("diff", "--cached", "--name-only", "--diff-filter=ACMR")
        changed = set(subjects)
        read = staged_content
    elif mode == "--worktree":
        subjects = sys.argv[2:]
        changed = worktree_changed() | set(subjects)
        read = lambda p: Path(p).read_text(encoding="utf-8", errors="replace") if Path(p).is_file() else None
    else:
        print(f"알 수 없는 모드: {mode}", file=sys.stderr)
        return 2
    seen = set()
    for rid, path, msg in check(subjects, changed, read):
        if (rid, path) in seen:
            continue
        seen.add((rid, path))
        print(f"[WARN:{rid}] {path} — {msg}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
