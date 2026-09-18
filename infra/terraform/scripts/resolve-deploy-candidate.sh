#!/usr/bin/env bash

set -euo pipefail

repository="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
latest_ref="${1:-refs/remotes/origin/dev}"
branch="${2:-dev}"
trigger_sha="${3:-}"

successful_revisions="$({
  if [ -n "${trigger_sha}" ]; then
    git rev-parse "${trigger_sha}"
  fi
  gh api --paginate --method GET \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2026-03-10" \
    "/repos/${repository}/actions/workflows/ci.yml/runs" \
    -f branch="${branch}" \
    -f event=push \
    -f status=success \
    -f per_page=100 \
    --jq '.workflow_runs[].head_sha'
} | sort -u)"

while IFS= read -r revision; do
  if grep -Fqx -- "${revision}" <<< "${successful_revisions}"; then
    echo "${revision}"
    exit 0
  fi
done < <(git rev-list --first-parent "${latest_ref}")

echo "No CI-successful revision exists on the current ${branch} first-parent history." >&2
exit 1
