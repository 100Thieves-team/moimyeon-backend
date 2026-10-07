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
    ("평문 비밀값 대입", re.compile(r"(?i)\b(password|passwd|secret|api[_-]?key|access[_-]?token)\b\s*[:=]\s*[\"']?(?!\$\{)([^\"'\s$]{8,})[\"']?\s*(?:[#;].*)?$")),
    ("Stripe류 키", re.compile(r"\bsk_(live|test)_[A-Za-z0-9]{10,}\b")),
    ("Google API 키", re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b")),
]
ALLOW_MARK = "gate:allow-secret"


# 예외는 **매치된 그 자리**를 근거로만 준다. 줄 어딘가에 무엇이 있는지로
# 판정하면 그것을 덧붙이는 것만으로 우회된다 — 실제로 `${` 포함 여부를 줄
# 전체에서 보다가 `password: RealSecret1234 # ... ${VAR}` 를 통과시켰다  gate:allow-secret
# (2026-08-25 Security Sentinel). 예외는 둘뿐이다:
#   1) 매치가 `${...}` **안쪽**일 때 — placeholder 안의 이름은 값이 아니라 참조다
#      (`password: ${storage.database.core-db.password:moimyeon}`의 뒤쪽 password)
#   2) 값이 키 이름 그 자체일 때 (`const val ACCESS_TOKEN = "ACCESS_TOKEN"`)


def _normalize(s: str) -> str:
    return s.lower().replace("_", "").replace("-", "")


def _inside_placeholder(line: str, pos: int) -> bool:
    head = line[:pos]
    return head.count("${") > head.count("}")


def _is_false_positive(line: str, m: "re.Match") -> bool:
    if _inside_placeholder(line, m.start()):
        return True
    return _normalize(m.group(1)) == _normalize(m.group(2))


def scan(lines):
    hits = []
    for where, line in lines:
        if ALLOW_MARK in line:
            continue
        for name, pat in PATTERNS:
            m = pat.search(line)
            if not m:
                continue
            if name == "평문 비밀값 대입" and _is_false_positive(line, m):
                continue
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
