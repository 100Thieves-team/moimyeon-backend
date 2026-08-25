#!/usr/bin/env bash

set -euo pipefail

branch="${1:?source branch is required}"
source_sha="${2:?source SHA is required}"
repository="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
wait_seconds="${TERRAFORM_APPLY_WAIT_SECONDS:-5400}"
poll_seconds="${TERRAFORM_APPLY_POLL_SECONDS:-15}"

case "${branch}" in dev|main) ;; *) exit 1 ;; esac
[[ "${source_sha}" =~ ^[0-9a-f]{40}$ ]] || exit 1
[[ "${wait_seconds}" =~ ^[1-9][0-9]*$ ]] || exit 1
[[ "${poll_seconds}" =~ ^[1-9][0-9]*$ ]] || exit 1

expected_title="Terraform Apply ${branch}@${source_sha}"
deadline=$((SECONDS + wait_seconds))

while [ "${SECONDS}" -lt "${deadline}" ]; do
  runs="$(gh api --method GET \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2026-03-10" \
    "/repos/${repository}/actions/workflows/terraform-apply.yml/runs" \
    -f event=workflow_run \
    -f per_page=100)"
  run="$(jq -c \
    --arg title "${expected_title}" '
      [.workflow_runs[] | select(.display_title == $title)]
      | sort_by(.id)
      | last // empty
    ' <<< "${runs}")"

  if [ -z "${run}" ]; then
    echo "Waiting for Terraform Apply run ${expected_title} to appear."
    sleep "${poll_seconds}"
    continue
  fi

  status="$(jq -r '.status' <<< "${run}")"
  conclusion="$(jq -r '.conclusion // empty' <<< "${run}")"
  run_url="$(jq -r '.html_url' <<< "${run}")"
  echo "Terraform boundary ${status}${conclusion:+/${conclusion}}: ${run_url}"

  if [ "${status}" = "completed" ]; then
    if [ "${conclusion}" = "success" ]; then
      exit 0
    fi
    echo "Terraform Apply did not succeed for ${branch}@${source_sha}: ${conclusion}." >&2
    exit 1
  fi

  sleep "${poll_seconds}"
done

echo "Timed out waiting for Terraform Apply ${branch}@${source_sha}." >&2
exit 1
