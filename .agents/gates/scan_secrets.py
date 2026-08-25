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
    ("평문 비밀값 대입", re.compile(r"(?i)\b(password|passwd|secret|api[_-]?key|access[_-]?token)\b\s*[:=]\s*[\"']?(?!\$\{)[^\"'\s$]{8,}[\"']?\s*(?:[#;].*)?$")),
    ("Stripe류 키", re.compile(r"\bsk_(live|test)_[A-Za-z0-9]{10,}\b")),
    ("Google API 키", re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b")),
]
ALLOW_MARK = "gate:allow-secret"


# 키 이름을 그대로 값으로 쓴 상수(`const val ACCESS_TOKEN = "ACCESS_TOKEN"`)만
# 구조적으로 시크릿이 아니다 — 값과 키가 **실제로 같은지** 비교한다.
# 형태(CONSTANT_CASE)만 보는 예외는 `secret = "QK7XTPLM4NDVRWS9"` 같은 실제
# 대문자 시크릿을 통과시켰다 (2026-08-25 Security Sentinel이 잡아냈다).
ASSIGN_VALUE = re.compile(r"[:=]\s*[\"']?([^\"'\s]+)[\"']?\s*(?:[#;].*)?$")


def _is_false_positive(line: str) -> bool:
    if "${" in line:  # Spring placeholder 기본값
        return True
    m = ASSIGN_VALUE.search(line)
    if not m:
        return False
    value = m.group(1)
    key = re.search(r"([A-Za-z_][A-Za-z0-9_-]*)\s*[:=]", line)
    return bool(key and key.group(1).lower().replace("_", "") == value.lower().replace("_", ""))


def scan(lines):
    hits = []
    for where, line in lines:
        if ALLOW_MARK in line:
            continue
        for name, pat in PATTERNS:
            if name == "평문 비밀값 대입" and _is_false_positive(line):
                continue
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
