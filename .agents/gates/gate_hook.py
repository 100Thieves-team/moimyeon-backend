#!/usr/bin/env python3
"""L1 편집 시점 훅 (Claude/Codex PostToolUse) — Edit·Write 직후 호출된다.

비용 계약 (DR-025):
- LLM 호출 없음. 감시 대상이 아닌 파일이면 즉시 무출력 종료 → 토큰 0.
- 위반 시에만 stderr 한 줄 + exit 2 (모델 컨텍스트로 피드백).
- 같은 (파일, 규칙) 경고는 세션당 1회 (dedup 마커).
"""
import hashlib
import json
import os
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import check_pairings  # noqa: E402
import scan_secrets  # noqa: E402


def dedup_dir(root):
    h = hashlib.sha1(str(root).encode()).hexdigest()[:12]
    d = Path(tempfile.gettempdir()) / f"harness-gates-{h}"
    d.mkdir(exist_ok=True)
    return d


def already_warned(dd, key):  # key에 session_id 포함 — 세션당 1회 계약
    marker = dd / hashlib.sha1(key.encode()).hexdigest()
    if marker.exists():
        return True
    marker.touch()
    return False


def main():
    try:
        event = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        return 0
    file_path = (event.get("tool_input") or {}).get("file_path")
    if not file_path:
        return 0
    root = Path(event.get("cwd") or os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd())
    try:
        rel = str(Path(file_path).resolve().relative_to(root.resolve()))
    except ValueError:
        return 0  # 레포 밖 파일은 무시
    os.chdir(root)

    # 경로 필터 선행: 감시 대상이 아니면 즉시 종료
    watched = (
        rel.endswith((".kt", ".kts", ".properties", ".yml", ".yaml", ".sql", ".json", ".toml", ".env", ".md", ".sh"))
    )
    if not watched:
        return 0

    session = str(event.get("session_id", "global"))
    dd = dedup_dir(root)
    messages = []

    # 시크릿: 편집된 파일 전문. 훅은 diff를 볼 수 없어 기존 줄까지 걸리므로
    # 세션당 1회만 알린다 — 확정 차단은 diff 기반인 pre-commit·CI가 한다.
    if Path(rel).is_file():
        lines = [(rel, line) for line in open(rel, encoding="utf-8", errors="replace")]
        for where, name in scan_secrets.scan(lines):
            if not already_warned(dd, f"{session}:secret:{name}:{where}"):
                messages.append(f"[BLOCK] 시크릿 의심: {name} — {where}. 커밋 전에 제거하라.")

    # 정합성 페어링: WARN, 세션당 1회
    changed = check_pairings.worktree_changed() | {rel}
    read = lambda p: Path(p).read_text(encoding="utf-8", errors="replace") if Path(p).is_file() else None
    for rid, path, msg in check_pairings.check([rel], changed, read):
        if not already_warned(dd, f"{session}:{rid}:{path}"):
            messages.append(f"[WARN:{rid}] {msg}")

    # application.yml이면 프로파일 싱크 (세션당 1회)
    if rel.endswith("application.yml") and "/main/" in rel and not already_warned(dd, f"{session}:profiles:{rel}"):
        import subprocess
        r = subprocess.run([sys.executable, str(Path(__file__).parent / "check_config_profiles.py"), rel],
                           capture_output=True, text=True)
        if r.returncode != 0:
            messages.append(r.stdout.strip())

    if messages:
        print("\n".join(messages), file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
