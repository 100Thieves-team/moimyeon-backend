#!/usr/bin/env bash

# Prints the task definition and the image an ECS service runs right now, once
# it is settled (one completed deployment, all desired tasks running):
#   task_definition_arn=<arn>
#   image_uri=<image reference as registered>
# Dev registers commit tags (repo:dev-<sha12>) in an IMMUTABLE repository and
# promotion registers digests; either is printed as is. Fails unless the
# service is settled on one completed deployment, so a half-finished rollout is
# never carried into a deployment bundle (MOI-590).
#
# Usage: resolve-running-service.sh <cluster> <service> <container>

set -euo pipefail

cluster="${1:?cluster is required}"
service="${2:?service is required}"
container="${3:?container is required}"

task_definition_arn="$(aws ecs describe-services --cluster "${cluster}" --services "${service}" --output json \
  | jq -r '.services[0]
    | if (.deployments | length) == 1
        and .deployments[0].status == "PRIMARY"
        and (.deployments[0].rolloutState // "COMPLETED") == "COMPLETED"
        and .runningCount == .desiredCount and .pendingCount == 0
      then .deployments[0].taskDefinition else empty end')"
if [[ ! "${task_definition_arn}" =~ ^arn:aws[a-zA-Z-]*:ecs:[a-z0-9-]+:[0-9]{12}:task-definition/[A-Za-z0-9_-]+:[0-9]+$ ]]; then
  echo "${service} is not settled on one completed deployment." >&2
  exit 1
fi

image_uri="$(aws ecs describe-task-definition --task-definition "${task_definition_arn}" --output json \
  | jq -r --arg container "${container}" '[.taskDefinition.containerDefinitions[] | select(.name == $container) | .image] | if length == 1 then .[0] else empty end')"
if [ -z "${image_uri}" ] || [[ "${image_uri}" =~ [[:space:]] ]]; then
  echo "${service} has no single ${container} image." >&2
  exit 1
fi

echo "task_definition_arn=${task_definition_arn}"
echo "image_uri=${image_uri}"
