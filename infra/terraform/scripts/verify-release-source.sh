#!/usr/bin/env bash

set -euo pipefail

source_sha="${1:?full source SHA is required}"
repository="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"

if [[ ! "${source_sha}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Source SHA must be a full lowercase 40-character commit SHA." >&2
  exit 1
fi

git cat-file -e "${source_sha}^{commit}" 2>/dev/null || {
  echo "Source SHA is not present in the checked-out repository: ${source_sha}." >&2
  exit 1
}

git merge-base --is-ancestor "${source_sha}" refs/remotes/origin/dev || {
  echo "Source SHA is not on the current dev history: ${source_sha}." >&2
  exit 1
}

successful_runs="$(gh api --method GET \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2026-03-10" \
  "/repos/${repository}/actions/workflows/ci.yml/runs" \
  -f branch=dev \
  -f event=push \
  -f status=success \
  -f head_sha="${source_sha}" \
  -f per_page=1 \
  --jq '.total_count')"

if [ "${successful_runs}" -lt 1 ]; then
  echo "Source SHA has no successful dev push CI run: ${source_sha}." >&2
  exit 1
fi

echo "Release source is CI-verified on dev: ${source_sha}."
