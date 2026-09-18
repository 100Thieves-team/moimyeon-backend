#!/usr/bin/env python3
"""Validate a Terraform ECS template and build a RegisterTaskDefinition request."""

import argparse
import copy
import json
from pathlib import Path
import re
import sys


# https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_RegisterTaskDefinition.html
REGISTER_FIELDS = frozenset({
    "family", "taskRoleArn", "executionRoleArn", "networkMode", "containerDefinitions",
    "volumes", "placementConstraints", "requiresCompatibilities", "cpu", "memory",
    "tags", "pidMode", "ipcMode", "proxyConfiguration", "inferenceAccelerators",
    "ephemeralStorage", "runtimePlatform", "enableFaultInjection",
})


def validate_template(template, expected_arn, container):
    if template.get("taskDefinitionArn") != expected_arn:
        raise ValueError("Task definition differs from the Terraform template")
    if template.get("status") != "ACTIVE":
        raise ValueError("Terraform task template is not ACTIVE; refresh Terraform outputs")
    family = expected_arn.rsplit("/", 1)[-1].rsplit(":", 1)[0]
    if template.get("family") != family:
        raise ValueError("Task definition family mismatch")
    containers = template.get("containerDefinitions", [])
    if sum(item.get("name") == container for item in containers) != 1:
        raise ValueError("Expected exactly one deployment container")


def prepare(template, expected_arn, container, image, release):
    validate_template(template, expected_arn, container)
    if not re.fullmatch(r"[0-9a-f]{40}", release) or not image or any(c.isspace() for c in image):
        raise ValueError("Invalid deployment image or source SHA")
    result = copy.deepcopy({key: value for key, value in template.items() if key in REGISTER_FIELDS})
    target = next(item for item in result["containerDefinitions"] if item["name"] == container)
    target["image"] = image
    target["environment"] = [
        item for item in (target.get("environment") or []) if item["name"] != "APP_RELEASE"
    ] + [{"name": "APP_RELEASE", "value": release}]
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--file", required=True)
    parser.add_argument("--expected-arn", required=True)
    parser.add_argument("--container", required=True)
    parser.add_argument("--image")
    parser.add_argument("--release")
    parser.add_argument("--validate-only", action="store_true")
    args = parser.parse_args()
    try:
        template = json.loads(Path(args.file).read_text())
        if args.validate_only:
            validate_template(template, args.expected_arn, args.container)
        else:
            if args.image is None or args.release is None:
                raise ValueError("Image and release are required")
            print(json.dumps(prepare(template, args.expected_arn, args.container, args.image, args.release)))
    except (ValueError, KeyError, TypeError, AttributeError, OSError) as error:
        # No raw task definitions or application environment values in diagnostics.
        print(f"ECS template validation failed ({type(error).__name__}); refusing registration.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
