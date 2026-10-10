#!/usr/bin/env bash

# Tells whether a deployment bundle is already recorded for a source SHA and,
# if so, which task definitions it fixed (MOI-589):
#   recorded=true|false
#   api_task_definition_arn=<arn>        (recorded only)
#   worker_task_definition_arn=<arn|>    (recorded only)
# A retry of the same commit must reuse these exact revisions: registering new
# ones would conflict with the immutable bundle. Only a missing parameter means
# "not recorded"; any other lookup error stops the deploy.

set -euo pipefail

parameter_prefix="${1:?deployment bundle parameter prefix is required}"
environment="${2:?environment is required}"
source_sha="${3:?source SHA is required}"

case "${environment}" in dev|live) ;; *) exit 1 ;; esac
[[ "${source_sha}" =~ ^[0-9a-f]{40}$ ]] || exit 1

parameter_name="${parameter_prefix%/}/${source_sha:0:12}"
if ! lookup_error="$(aws ssm get-parameter --name "${parameter_name}" --query 'Parameter.Name' --output text 2>&1 >/dev/null)"; then
  if grep -q 'ParameterNotFound' <<< "${lookup_error}"; then
    echo "recorded=false"
    exit 0
  fi
  echo "Deployment bundle lookup failed for ${parameter_name}: $(head -c 300 <<< "${lookup_error}")" >&2
  exit 1
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
manifest="$(bash "${script_dir}/read-deployment-bundle.sh" "${parameter_prefix}" "${environment}" "${source_sha}")"
echo "recorded=true"
echo "api_task_definition_arn=$(jq -r '.api.taskDefinitionArn' <<< "${manifest}")"
echo "worker_task_definition_arn=$(jq -r '.worker.taskDefinitionArn // empty' <<< "${manifest}")"
