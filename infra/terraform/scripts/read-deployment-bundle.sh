#!/usr/bin/env bash

set -euo pipefail

parameter_prefix="${1:?deployment bundle parameter prefix is required}"
environment="${2:?environment is required}"
source_sha="${3:?source SHA is required}"

case "${environment}" in dev|live) ;; *) exit 1 ;; esac
[[ "${source_sha}" =~ ^[0-9a-f]{40}$ ]] || exit 1

parameter_name="${parameter_prefix%/}/${source_sha:0:12}"
manifest="$(aws ssm get-parameter \
  --name "${parameter_name}" \
  --query 'Parameter.Value' \
  --output text)"

if ! jq -e \
  --arg environment "${environment}" \
  --arg source_sha "${source_sha}" '
  .environment == $environment
  and .sourceSha == $source_sha
  and (.api.imageUri | test("@sha256:[0-9a-f]{64}$"))
  and (.api.taskDefinitionArn | test("^arn:aws[a-zA-Z-]*:ecs:[a-z0-9-]+:[0-9]{12}:task-definition/[A-Za-z0-9_-]+:[0-9]+$"))
  and (
    .worker == null
    or (
      (.worker.imageUri | test("@sha256:[0-9a-f]{64}$"))
      and (.worker.taskDefinitionArn | test("^arn:aws[a-zA-Z-]*:ecs:[a-z0-9-]+:[0-9]{12}:task-definition/[A-Za-z0-9_-]+:[0-9]+$"))
    )
  )
' <<< "${manifest}" >/dev/null; then
  echo "Deployment bundle manifest is invalid: ${parameter_name}." >&2
  exit 1
fi

jq -c . <<< "${manifest}"
