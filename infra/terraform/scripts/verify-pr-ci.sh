#!/usr/bin/env bash

# Proves PR CI verified deploy_sha's exact tree, then prints:
#   tree=<merged tree>
#   candidate_tag=<tag of the image that same CI run built, or empty>
#
# The dev ruleset requires a PR, the `build` check, and an up-to-date branch
# (strict status checks), so the merge or squash commit has the same tree as
# the PR head that CI checked. This replaces waiting for a second CI run on the
# dev push (MOI-565). Fail closed on anything else.
#
# The candidate tag embeds the run ID and attempt of the successful build, and
# is accepted only when that same run attempt's `image` job succeeded. Tags are
# immutable, so a tag pushed first by any other workflow makes that image job
# fail and the deploy builds instead of trusting it.

set -euo pipefail

repository="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
deploy_sha="${1:?deploy SHA is required}"
branch="${2:?target branch is required}"
check_name="${VERIFY_PR_CI_CHECK_NAME:-build}"
image_job_name="${VERIFY_PR_CI_IMAGE_JOB_NAME:-image}"

[[ "${deploy_sha}" =~ ^[0-9a-f]{40}$ ]] || exit 1
case "${branch}" in dev) ;; *) exit 1 ;; esac

github_api() {
  gh api --method GET \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2026-03-10" \
    "$@"
}

deploy_tree="$(git rev-parse "${deploy_sha}^{tree}")"

# API version 2026-03-10 no longer returns merge_commit_sha (null), so the PR
# is chosen by what actually matters: merged into the branch, and its head has
# exactly the merged tree. Candidates come from the commit-to-PR association,
# then from the most recently updated closed PRs while that index catches up.
# Retry for about two minutes before refusing.
merged_pulls_filter="[.[] | select(.base.ref == \"${branch}\" and .merged_at != null) | \"\\(.number) \\(.head.sha)\"] | .[]"
pull_number=""
head_sha=""
# API failures are reported but retried: they must not read as "no PR".
api_or_warn() {
  local output error_file
  error_file="$(mktemp)"
  if output="$(github_api "$@" 2>"${error_file}")"; then
    rm -f "${error_file}"
    printf '%s' "${output}"
    return 0
  fi
  echo "GitHub API call failed ($1): $(head -c 300 "${error_file}")" >&2
  rm -f "${error_file}"
  return 1
}
find_verified_pull() {
  local candidates number sha tree
  candidates="$(api_or_warn "/repos/${repository}/commits/${deploy_sha}/pulls" --jq "${merged_pulls_filter}" || true)"
  if [ -z "${candidates}" ]; then
    # Closed-but-unmerged PRs can fill a short page, so read a wider page and
    # tree-check only the five most recently updated merged PRs.
    candidates="$(api_or_warn "/repos/${repository}/pulls" \
      -f state=closed -f base="${branch}" -f sort=updated -f direction=desc -f per_page=50 \
      --jq "${merged_pulls_filter}" || true)"
    candidates="$(head -n 5 <<< "${candidates}")"
  fi
  while read -r number sha; do
    [[ "${number}" =~ ^[1-9][0-9]*$ ]] && [[ "${sha}" =~ ^[0-9a-f]{40}$ ]] || continue
    # Squash merges leave the PR head off dev history; read its tree from the API.
    tree="$(api_or_warn "/repos/${repository}/git/commits/${sha}" --jq '.tree.sha' || true)"
    if [ "${tree}" = "${deploy_tree}" ]; then
      pull_number="${number}"
      head_sha="${sha}"
      return 0
    fi
  done <<< "${candidates}"
  return 1
}
attempts="${VERIFY_PR_CI_ATTEMPTS:-12}"
[[ "${attempts}" =~ ^[1-9][0-9]*$ ]] || exit 1
for ((attempt = 1; attempt <= attempts; attempt++)); do
  find_verified_pull && break
  if [ "${attempt}" -lt "${attempts}" ]; then
    echo "No merged PR with tree ${deploy_tree} is visible yet (attempt ${attempt}/${attempts})." >&2
    sleep "${VERIFY_PR_CI_RETRY_SECONDS:-10}"
  fi
done
if [ -z "${head_sha}" ]; then
  echo "No PR merged into ${branch} has the tree of ${deploy_sha}; CI did not verify this revision." >&2
  exit 1
fi

check_run="$(github_api "/repos/${repository}/commits/${head_sha}/check-runs" \
  -f check_name="${check_name}" \
  -f filter=latest \
  --jq '[.check_runs[] | select(.app.slug == "github-actions" and .status == "completed")] | sort_by(.started_at) | last // empty | tojson')"
conclusion="$(jq -r '.conclusion // empty' <<< "${check_run:-null}")"
if [ "${conclusion}" != "success" ]; then
  echo "PR #${pull_number} ${check_name} check on ${head_sha} is '${conclusion:-missing}', not success." >&2
  exit 1
fi

echo "PR #${pull_number} CI verified tree ${deploy_tree} for ${deploy_sha}." >&2
echo "tree=${deploy_tree}"

# Candidate lookup only speeds the deploy up; any API failure here means
# "build the image" and never fails the verified deploy.
candidate_tag=""
candidate_not_before=""
find_candidate() {
  local job_id job run_id run_attempt job_head_sha image_conclusion
  # An Actions check run ID is its job ID.
  job_id="$(jq -r '.id' <<< "${check_run}")"
  [[ "${job_id}" =~ ^[1-9][0-9]*$ ]] || return 0
  job="$(github_api "/repos/${repository}/actions/jobs/${job_id}" \
    --jq '{run_id, run_attempt, head_sha, started_at} | tojson' 2>/dev/null)" || return 0
  run_id="$(jq -r '.run_id' <<< "${job}")"
  run_attempt="$(jq -r '.run_attempt' <<< "${job}")"
  job_head_sha="$(jq -r '.head_sha' <<< "${job}")"
  [[ "${run_id}" =~ ^[1-9][0-9]*$ ]] && [[ "${run_attempt}" =~ ^[1-9][0-9]*$ ]] || return 0
  [ "${job_head_sha}" = "${head_sha}" ] || return 0
  image_conclusion="$(github_api "/repos/${repository}/actions/runs/${run_id}/attempts/${run_attempt}/jobs" \
    --jq "[.jobs[] | select(.name == \"${image_job_name}\")] | last | .conclusion // empty" 2>/dev/null)" || return 0
  [ "${image_conclusion}" = "success" ] || return 0
  candidate_tag="tree-${deploy_tree}-run-${run_id}-${run_attempt}"
  # The image must have been pushed after this run started: an expired tag can
  # be pushed again later by another workflow.
  candidate_not_before="$(jq -r '.started_at // empty' <<< "${job}")"
}
find_candidate || true
if [ -z "${candidate_tag}" ]; then
  echo "No PR-built image from the verified CI run; the deploy builds it." >&2
fi
echo "candidate_tag=${candidate_tag}"
echo "candidate_not_before=${candidate_not_before}"
