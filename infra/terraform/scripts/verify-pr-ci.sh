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

# GitHub can link a fresh merge commit to its PR a few seconds after the push.
pull=""
for attempt in 1 2 3 4; do
  pull="$(github_api "/repos/${repository}/commits/${deploy_sha}/pulls" \
    --jq "[.[] | select(.merge_commit_sha == \"${deploy_sha}\" and .base.ref == \"${branch}\" and .merged_at != null)] | first // empty | tojson")"
  [ -n "${pull}" ] && break
  [ "${attempt}" -lt 4 ] && sleep "${VERIFY_PR_CI_RETRY_SECONDS:-5}"
done
if [ -z "${pull}" ]; then
  echo "No PR merged into ${branch} produced ${deploy_sha}; refusing an unverified revision." >&2
  exit 1
fi

pull_number="$(jq -r '.number' <<< "${pull}")"
head_sha="$(jq -r '.head.sha' <<< "${pull}")"
[[ "${pull_number}" =~ ^[1-9][0-9]*$ ]] || exit 1
[[ "${head_sha}" =~ ^[0-9a-f]{40}$ ]] || exit 1

# Squash merges leave the PR head off dev history; read its tree from the API.
head_tree="$(github_api "/repos/${repository}/git/commits/${head_sha}" --jq '.tree.sha')"
if [ "${head_tree}" != "${deploy_tree}" ]; then
  echo "PR #${pull_number} head ${head_sha} does not have the merged tree; CI did not verify ${deploy_sha}." >&2
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
