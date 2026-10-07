#!/usr/bin/env python3
"""Validate the allowlisted, non-secret dev deploy wiring that Terraform publishes to SSM."""

import argparse
import json
import os
from pathlib import Path
import re
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
CANDIDATE_FAMILIES = ("core-api", "core-worker")
SAFE_VALUE = r"[A-Za-z0-9_./:@-]+"


def validate(document, role_arn):
    if not isinstance(document, dict):
        raise ValueError("Deployment config must be an object")
    if set(document) != {"schema_version", "environment", "config", "candidate_repository_urls"}:
        raise ValueError("Unexpected deployment config fields")
    if document["schema_version"] != 2 or document["environment"] != "dev":
        raise ValueError("Deployment config is not the dev schema")
    config = document["config"]
    if not isinstance(config, dict) or set(config) != set(OUTPUTS):
        raise ValueError("Deployment config must contain exactly the non-secret output allowlist")
    for key, value in config.items():
        # These identifiers/URLs never require shell metacharacters or multiline outputs.
        if not isinstance(value, str) or not re.fullmatch(SAFE_VALUE, value):
            raise ValueError(f"Missing or unsafe deployment config value: {key}")
    role = re.fullmatch(r"arn:aws:iam::([0-9]{12}):role/[A-Za-z0-9_./-]+", config["role_arn"])
    if role is None or config["role_arn"] != role_arn:
        # The workflow assumed role_arn to read this document; both must agree.
        raise ValueError("Deployment role ARN does not match the assumed role")
    for prefix, family in (("", "core-api"), ("worker_", "core-worker")):
        arn_prefix = f"arn:aws:ecs:{config['aws_region']}:{role[1]}:task-definition/moimyeon-dev-{family}:"
        if not re.fullmatch(re.escape(arn_prefix) + r"[1-9][0-9]*", config[f"{prefix}ecs_task_definition"]):
            raise ValueError("Task template must be an exact dev revision in the deployment account/region")
    candidates = document["candidate_repository_urls"]
    if not isinstance(candidates, dict) or set(candidates) != set(CANDIDATE_FAMILIES):
        raise ValueError("Deployment config must name both candidate repositories")
    registry = config["ecr_repository_url"].split("/", 1)[0]
    for family, url in candidates.items():
        if not isinstance(url, str) or not re.fullmatch(re.escape(registry) + r"/[a-z0-9._/-]+-candidate", url):
            raise ValueError(f"Candidate repository must be in the deploy registry: {family}")
    return config, candidates


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("read",))
    parser.add_argument("--file", required=True)
    parser.add_argument("--role-arn", required=True)
    args = parser.parse_args()
    try:
        config, candidates = validate(json.loads(Path(args.file).read_text()), args.role_arn)
        # Validate the entire document before emitting any Actions outputs.
        with open(os.environ["GITHUB_OUTPUT"], "a") as output:
            output.write("name=dev\necs_health_check_grace_seconds=240\n")
            output.writelines(f"{key}={value}\n" for key, value in config.items())
            output.write(f"candidate_repository_url={candidates['core-api']}\n")
            output.write(f"worker_candidate_repository_url={candidates['core-worker']}\n")
    except (ValueError, KeyError, TypeError, OSError):
        # Do not echo file contents or untrusted field values.
        print("Invalid or unavailable Terraform deployment config; refusing deployment.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
