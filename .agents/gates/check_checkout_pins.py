#!/usr/bin/env python3
"""CI job 수와 관계없이 실제 checkout step의 승인된 SHA 고정을 검사한다."""

import argparse
from pathlib import Path

import yaml


APPROVED_CHECKOUT = "actions/checkout@11d5960a326750d5838078e36cf38b85af677262"


def validate_workflow(workflow):
    if not isinstance(workflow, dict) or not isinstance(workflow.get("jobs"), dict):
        return ["workflow의 jobs mapping이 필요하다"]

    errors = []
    checkout_count = 0
    for job_name, job in workflow["jobs"].items():
        if not isinstance(job, dict):
            errors.append(f"{job_name}: job mapping이 필요하다")
            continue
        steps = job.get("steps", [])  # reusable workflow jobs에는 steps가 없다.
        if not isinstance(steps, list):
            errors.append(f"{job_name}: steps 목록이 필요하다")
            continue
        for index, step in enumerate(steps, 1):
            if not isinstance(step, dict):
                errors.append(f"{job_name} step {index}: step mapping이 필요하다")
                continue
            uses = step.get("uses")
            if uses is None:
                continue
            if not isinstance(uses, str):
                errors.append(f"{job_name} step {index}: uses 문자열이 필요하다")
                continue
            # 주석·run 본문의 문자열은 검사 대상이 아니다. if 조건이 붙은
            # step도 실행 가능성이 있으므로 동일한 정책으로 검증한다.
            if uses.strip().partition("@")[0].lower() == "actions/checkout":
                checkout_count += 1
                if uses != APPROVED_CHECKOUT:
                    errors.append(f"{job_name} step {index}: checkout이 승인된 SHA로 고정되지 않았다")

    if checkout_count == 0:
        errors.append("검사할 actions/checkout step이 없다")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("workflow", nargs="?", default=".github/workflows/ci.yml", type=Path)
    path = parser.parse_args().workflow
    try:
        workflow = yaml.safe_load(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, yaml.YAMLError):
        print(f"[BLOCK] checkout pin: workflow를 읽거나 YAML을 파싱할 수 없다 ({path})")
        return 1
    errors = validate_workflow(workflow)
    for error in errors:
        print(f"[BLOCK] checkout pin: {error}")
    if not errors:
        print("모든 checkout step의 승인된 SHA 고정 확인")
    return int(bool(errors))


if __name__ == "__main__":
    raise SystemExit(main())
