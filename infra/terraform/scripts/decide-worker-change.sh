#!/usr/bin/env bash

# Decides whether a dev deploy replaces the Notification Worker (MOI-590).
# Prints, for $GITHUB_OUTPUT:
#   deploy=true|false
#   reason=<why>
#   running_task_definition_arn=<arn>   (deploy=false only)
#   running_image_uri=<image>           (deploy=false only)
# Exits 1, before anything changes, when a retry of a commit that kept an older
# Worker finds a different Worker running.
#
# Inputs (environment): DEPLOY_SHA, WORKER_CANDIDATE (promotion result),
# WORKER_IMAGE_URI (this commit's image), WORKER_REPOSITORY, CLUSTER,
# WORKER_SERVICE, WORKER_CONTAINER, WORKER_TEMPLATE (ARN), TEMPLATE_FILE
# (the template's describe output), WORK_DIR.

set -euo pipefail

: "${DEPLOY_SHA:?}" "${WORKER_IMAGE_URI:?}" "${WORKER_REPOSITORY:?}" "${CLUSTER:?}" "${WORKER_SERVICE:?}"
: "${WORKER_CONTAINER:?}" "${WORKER_TEMPLATE:?}" "${TEMPLATE_FILE:?}" "${WORK_DIR:?}"
[[ "${DEPLOY_SHA}" =~ ^[0-9a-f]{40}$ ]] || exit 1

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "${script_dir}/../../.." && pwd)"

replace() {
  echo "deploy=true"
  echo "reason=$1"
  exit 0
}
keep() {
  echo "deploy=false"
  echo "reason=$1"
  echo "running_task_definition_arn=${running_task_definition}"
  echo "running_image_uri=${running_image}"
  exit 0
}
digest_of() {
  docker buildx imagetools inspect "$1" --format '{{.Manifest.Digest}}' 2>/dev/null || true
}

running_task_definition=""
running_image=""
if running="$(bash "${script_dir}/resolve-running-service.sh" "${CLUSTER}" "${WORKER_SERVICE}" "${WORKER_CONTAINER}")"; then
  running_task_definition="$(sed -n 's/^task_definition_arn=//p' <<< "${running}")"
  running_image="$(sed -n 's/^image_uri=//p' <<< "${running}")"
fi

# A retry follows this commit's Worker marker. An earlier attempt set it before
# recording: it names the image this commit deployed or kept.
marker_digest="$(digest_of "${WORKER_REPOSITORY}:deployed-dev-${DEPLOY_SHA::12}")"
if [ -n "${marker_digest}" ]; then
  [ "${marker_digest}" != "$(digest_of "${WORKER_IMAGE_URI}")" ] \
    || replace "an earlier attempt deployed this commit's Worker"
  if [ -n "${running_image}" ] && [ "$(digest_of "${running_image}")" = "${marker_digest}" ]; then
    keep "an earlier attempt kept this Worker for this commit"
  fi
  echo "This commit kept an older Worker in an earlier attempt, but the running Worker is not that one (rolled back or rolling out). Deploy a new commit." >&2
  exit 1
fi

case "${WORKER_CANDIDATE:-}" in
  promoted|existing) ;;
  *) replace "no PR-built image (${WORKER_CANDIDATE:-none})" ;;
esac
[ -n "${running_task_definition}" ] || replace "the running Worker is not settled"

new_input="$(bash "${root_dir}/.github/scripts/runtime-input.sh" read "${WORKER_IMAGE_URI}")"
running_input="$(bash "${root_dir}/.github/scripts/runtime-input.sh" read "${running_image}" || true)"
[ -n "${new_input}" ] || replace "the new image has no runtime input label"
[ "${new_input}" = "${running_input}" ] || replace "the boot jar or image definition changed"

aws ecs describe-task-definition --task-definition "${running_task_definition}" \
  --query taskDefinition > "${WORK_DIR}/worker-running-task-definition.json"
same="$(python3 "${script_dir}/prepare_ecs_task.py" \
  --file "${TEMPLATE_FILE}" --expected-arn "${WORKER_TEMPLATE}" \
  --container "${WORKER_CONTAINER}" --compare-running "${WORK_DIR}/worker-running-task-definition.json")"
[ "${same}" = "same=true" ] || replace "the Terraform task template changed"

keep "this commit cannot change it"
