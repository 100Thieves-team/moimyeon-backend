#!/usr/bin/env python3
"""시크릿 패턴 검출 (BLOCK) — False Negative 최소화 우선.

사용:
  scan_secrets.py --staged            # 커밋 예정 diff의 추가 줄 (pre-commit)
  scan_secrets.py --range A...B       # git range의 추가 줄 (CI)
  scan_secrets.py --files p1 p2 ...   # 파일 전문 (훅·테스트)

의도적 예시 값은 해당 줄에 'gate:allow-secret' 주석을 달아 예외 처리한다.
"""
import re
import subprocess
import sys

PATTERNS = [
    ("AWS 액세스 키", re.compile(r"\b(AKIA|ASIA)[0-9A-Z]{16}\b")),
    ("private key 블록", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY")),
    ("GitHub 토큰", re.compile(r"\b(ghp|gho|ghu|ghs)_[A-Za-z0-9]{36}\b|\bgithub_pat_[A-Za-z0-9_]{22,}\b")),
    ("Slack 토큰", re.compile(r"\bxox[baprs]-[A-Za-z0-9-]{10,}\b")),
    ("API 키 형태(sk-)", re.compile(r"\bsk-[A-Za-z0-9_-]{20,}\b")),
    ("JWT", re.compile(r"\beyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\b")),
    ("URL 내 자격증명", re.compile(r"\b[a-z][a-z0-9+.-]*://[^/\s:@]+:[^@\s]{4,}@")),
    ("평문 비밀값 대입", re.compile(r"(?i)\b(password|passwd|secret|api[_-]?key|access[_-]?token)\b\s*[:=]\s*[\"'][^\"'\s]{8,}[\"']")),
]
ALLOW_MARK = "gate:allow-secret"


def scan(lines):
    hits = []
    for where, line in lines:
        if ALLOW_MARK in line:
            continue
        for name, pat in PATTERNS:
            if pat.search(line):
                hits.append((where, name))
    return hits


def diff_added_lines(args):
    out = subprocess.run(["git", "diff", "-U0", *args], capture_output=True, text=True).stdout
    lines, current = [], "?"
    for line in out.splitlines():
        if line.startswith("+++ b/"):
            current = line[6:]
        elif line.startswith("+") and not line.startswith("+++"):
            lines.append((current, line[1:]))
    return lines


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "--staged"
    if mode == "--staged":
        lines = diff_added_lines(["--cached"])
    elif mode == "--range":
        lines = diff_added_lines([sys.argv[2]])
    elif mode == "--files":
        lines = []
        for p in sys.argv[2:]:
            try:
                for line in open(p, encoding="utf-8", errors="replace"):
                    lines.append((p, line))
            except OSError:
                pass
    else:
        print(f"알 수 없는 모드: {mode}", file=sys.stderr)
        return 2
    hits = scan(lines)
    if hits:
        for where, name in hits:
            print(f"[BLOCK] 시크릿 의심: {name} — {where}")
        print("의도적 예시 값이면 해당 줄에 'gate:allow-secret' 주석을 단다.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
