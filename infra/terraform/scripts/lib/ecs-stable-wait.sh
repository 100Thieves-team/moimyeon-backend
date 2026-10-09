#!/usr/bin/env bash
# Sourced by deploy-ecs-image.sh. Callers set `cluster` and `service`.
# shellcheck disable=SC2154
#
# The built-in ECS services-stable waiter gives up after 10 minutes (40 x 15s),
# shorter than a live blue/green rollout: instance scale-out, health grace and
# bake time together exceed it, so a healthy deploy was reported as failed and
# rolled back (MOI-581). Poll the same condition with an explicit deadline.

stable_timeout_seconds="${ECS_STABLE_TIMEOUT_SECONDS:-1800}"
stable_poll_seconds="${ECS_STABLE_POLL_SECONDS:-15}"
if [[ ! "${stable_timeout_seconds}" =~ ^[1-9][0-9]*$ ]]; then
  echo "ECS_STABLE_TIMEOUT_SECONDS must be a positive integer: ${stable_timeout_seconds}." >&2
  exit 1
fi
if [[ ! "${stable_poll_seconds}" =~ ^[1-9][0-9]*$ ]]; then
  echo "ECS_STABLE_POLL_SECONDS must be a positive integer: ${stable_poll_seconds}." >&2
  exit 1
fi

wait_for_service_stable() {
  local deadline=$((SECONDS + stable_timeout_seconds))
  local state deployments running desired rollout

  while [ "${SECONDS}" -lt "${deadline}" ]; do
    # A container health check (the Worker's, MOI-594) holds the rollout below
    # COMPLETED until tasks are HEALTHY; RUNNING alone is not ready.
    if state="$(aws ecs describe-services \
      --cluster "${cluster}" \
      --services "${service}" \
      --query "services[0].[length(deployments), runningCount, desiredCount, deployments[?status=='PRIMARY'] | [0].rolloutState]" \
      --output text)"; then
      read -r deployments running desired rollout <<< "${state}"
      if [ "${deployments}" = "1" ] && [ "${running}" = "${desired}" ] \
        && { [ "${rollout:-None}" = "COMPLETED" ] || [ "${rollout:-None}" = "None" ]; }; then
        return 0
      fi
    fi
    sleep "${stable_poll_seconds}"
  done
  echo "ECS service ${service} did not stabilize within ${stable_timeout_seconds}s." >&2
  return 1
}
