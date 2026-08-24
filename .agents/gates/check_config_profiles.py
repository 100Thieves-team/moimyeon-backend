#!/usr/bin/env python3
"""application.yml 배포 프로파일(dev/live) 키 싱크 검사 (BLOCK).

사용: check_config_profiles.py [yml ...]   (기본: core·admin 모듈의 main application.yml)

한쪽 배포 프로파일에만 있는 키는 배포 후 다른 환경에서 터진다.
의도적 프로파일 전용 키는 config-profile-allowlist.txt에 등록한다.
"""
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    import os
    print("PyYAML 없음 — 프로파일 검사 불가", file=sys.stderr)
    sys.exit(1 if os.environ.get("GITHUB_ACTIONS") else 0)

DEPLOY_PROFILES = {"dev", "live"}
PROFILE_KEY = "spring.config.activate.on-profile"
ALLOWLIST = Path(__file__).parent / "config-profile-allowlist.txt"


def flatten(d, prefix=""):
    keys = set()
    if isinstance(d, dict):
        for k, v in d.items():
            path = f"{prefix}.{k}" if prefix else str(k)
            sub = flatten(v, path)
            keys |= sub if sub else {path}
    return keys


def profile_of(doc):
    if not isinstance(doc, dict):
        return None
    if PROFILE_KEY in doc:
        return doc[PROFILE_KEY]
    try:
        return doc["spring"]["config"]["activate"]["on-profile"]
    except (KeyError, TypeError):
        return None


def default_targets():
    return [p for p in Path(".").glob("*/*/src/main/resources/application.yml")
            if ".worktrees" not in str(p) and not str(p).startswith(".")]


def main():
    targets = [Path(p) for p in sys.argv[1:]] or default_targets()
    allow = set()
    if ALLOWLIST.is_file():
        allow = {line.strip() for line in ALLOWLIST.read_text().splitlines()
                 if line.strip() and not line.startswith("#")}
    failures = []
    for f in targets:
        if not f.is_file():
            continue
        profiles = {}
        try:
            for doc in yaml.safe_load_all(f.read_text(encoding="utf-8")):
                prof = profile_of(doc)
                if prof in DEPLOY_PROFILES:
                    keys = flatten(doc) - {PROFILE_KEY, "spring.config.activate.on-profile",
                                           "spring", "spring.config", "spring.config.activate"}
                    keys = {k for k in keys if not k.startswith("spring.config.activate")}
                    profiles[prof] = keys
        except yaml.YAMLError as e:
            failures.append(f"{f}: YAML 파싱 실패 — {e}")
            continue
        if len(profiles) < 2:
            continue
        union = set().union(*profiles.values())
        for prof, keys in sorted(profiles.items()):
            missing = union - keys - allow
            for k in sorted(missing):
                failures.append(f"{f}: 프로파일 '{prof}'에 '{k}' 누락 (다른 배포 프로파일엔 존재)")
    if failures:
        for msg in failures:
            print(f"[BLOCK] {msg}")
        print("의도적 프로파일 전용 키는 .agents/gates/config-profile-allowlist.txt에 등록한다.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
