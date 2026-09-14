#!/usr/bin/env python3
"""Exchange only allowlisted, non-secret Terraform outputs with the dev deploy run."""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys


OUTPUTS = {
    "aws_region": "aws_region",
    "role_arn": "github_deploy_role_arn",
    "ecr_repository_url": "ecr_repository_url",
    "ecs_cluster": "ecs_cluster_name",
    "ecs_service": "ecs_service_name",
    "ecs_container": "ecs_container_name",
    "ecs_task_definition": "ecs_task_definition_arn",
    "image_uri_parameter": "image_uri_parameter_name",
    "app_url": "app_url",
    "bundle_parameter_prefix": "deployment_bundle_parameter_prefix",
    "worker_ecr_repository_url": "notification_worker_ecr_repository_url",
    "worker_ecs_service": "notification_worker_ecs_service_name",
    "worker_ecs_container": "notification_worker_ecs_container_name",
    "worker_ecs_task_definition": "notification_worker_task_definition_arn",
    "worker_image_uri_parameter": "notification_worker_image_uri_parameter_name",
}


def validate(document, sha, run_id, attempt):
    if not isinstance(document, dict):
        raise ValueError("Deployment config must be an object")
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Invalid source SHA")
    if not all(re.fullmatch(r"[1-9][0-9]*", x) for x in (run_id, attempt)):
        raise ValueError("Invalid Terraform run identity")
    expected = {
        "schema_version": 1, "environment": "dev", "source_sha": sha,
        "run_id": run_id, "run_attempt": attempt,
    }
    if set(document) != set(expected) | {"config"}:
        raise ValueError("Unexpected deployment config fields")
    if any(document.get(key) != value for key, value in expected.items()):
        raise ValueError("Deployment config does not match the successful Terraform run")
    config = document["config"]
    if not isinstance(config, dict) or set(config) != set(OUTPUTS):
        raise ValueError("Deployment config must contain exactly the non-secret output allowlist")
    for key, value in config.items():
        # These identifiers/URLs never require shell metacharacters or multiline outputs.
        if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_./:@-]+", value):
            raise ValueError(f"Missing or unsafe deployment config value: {key}")
    role = re.fullmatch(r"arn:aws:iam::([0-9]{12}):role/[A-Za-z0-9_./-]+", config["role_arn"])
    if role is None:
        raise ValueError("Invalid deployment role ARN")
    for prefix, family in (("", "core-api"), ("worker_", "core-worker")):
        arn_prefix = f"arn:aws:ecs:{config['aws_region']}:{role[1]}:task-definition/moimyeon-dev-{family}:"
        if not re.fullmatch(re.escape(arn_prefix) + r"[1-9][0-9]*", config[f"{prefix}ecs_task_definition"]):
            raise ValueError("Task template must be an exact dev revision in the deployment account/region")
    return config


def export_config(sha, run_id, attempt):
    wrapper = Path(__file__).with_name("terraform-command.sh")
    # Never use `terraform output -json`: state and other outputs may contain secrets.
    config = {
        key: subprocess.run(
            ["bash", str(wrapper), "output-raw", "dev", output],
            check=True, capture_output=True, text=True,
        ).stdout.strip()
        for key, output in OUTPUTS.items()
    }
    document = {
        "schema_version": 1, "environment": "dev", "source_sha": sha,
        "run_id": run_id, "run_attempt": attempt, "config": config,
    }
    validate(document, sha, run_id, attempt)
    return document


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("export", "read"))
    parser.add_argument("--sha", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--attempt", required=True)
    parser.add_argument("--file")
    args = parser.parse_args()
    try:
        if args.mode == "export":
            print(json.dumps(export_config(args.sha, args.run_id, args.attempt)))
        else:
            if not args.file:
                raise ValueError("Deployment config file is required")
            config = validate(json.loads(Path(args.file).read_text()), args.sha, args.run_id, args.attempt)
            # Validate the entire document before emitting any Actions outputs.
            with open(os.environ["GITHUB_OUTPUT"], "a") as output:
                output.write("name=dev\necs_health_check_grace_seconds=240\n")
                output.writelines(f"{key}={value}\n" for key, value in config.items())
    except (ValueError, KeyError, TypeError, OSError, subprocess.CalledProcessError):
        # Do not echo Terraform stderr, file contents, or untrusted field values.
        print("Invalid or unavailable Terraform deployment config; refusing deployment.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
