#!/usr/bin/env bash

# Reads changed paths (one per line) on stdin and prints runtime=true when any
# of them can change what an environment runs or how it is deployed, and
# runtime=false otherwise (MOI-592). The single source for:
#   - CI: whether a PR gets candidate images
#   - Deploy AWS: whether a dev commit deploys, and whether a newer commit supersedes it
#   - Promote Live: whether a main merge promotes, and which dev deployment it matches
#
# Usage: runtime-changes.sh <dev|live>
#
# Anything not listed below counts as a runtime change, so a new path deploys
# until someone decides otherwise. Workflows and scripts that build, deploy or
# promote stay runtime changes: they are exercised by deploying.

set -euo pipefail

environment="${1:?environment (dev or live) is required}"
case "${environment}" in
  dev) other_environment=live ;;
  live) other_environment=dev ;;
  *)
    echo "unknown environment: ${environment}" >&2
    exit 1
    ;;
esac

runtime=false
while IFS= read -r path; do
  [ -n "${path}" ] || continue
  case "${path}" in
    # Documentation (DR-005).
    docs/*|*.md|*.mdx) ;;
    # Agent harness, work logs, local hooks and repository tooling settings.
    .agents/*|.claude/*|.codex/*|.githooks/*|.worklog/*) ;;
    .editorconfig|.gitattributes|.gitignore|.gitleaks.toml|.gitleaksignore|.review-swarm.yaml|.env.example) ;;
    # Workflows the app deploy never runs (terraform-plan-environment.yml is
    # also called by Terraform Apply, which decides on its own whether to run).
    .github/workflows/api-docs-pages.yml|.github/workflows/linear-issue-for-pr.yml|.github/workflows/review-swarm.yml) ;;
    .github/workflows/terraform-plan.yml|.github/workflows/terraform-plan-environment.yml) ;;
    # CI-only helper scripts, by name: a new script here deploys until listed.
    .github/scripts/check_flyway_migrations.py|.github/scripts/test_*.py) ;;
    .github/scripts/create-linear-issue.mjs|.github/scripts/notify-api-spec-change.sh) ;;
    .github/scripts/openapi_operation_diff.py|.github/scripts/api-spec-alert-contract.sh) ;;
    # Test sources are not in the boot jars.
    */src/test/*|tests/*) ;;
    # Terraform contract tests and the other environment's values.
    infra/terraform/tests/*) ;;
    "infra/terraform/envs/${other_environment}/"*) ;;
    # The monitoring host is provisioned by Terraform, not deployed with the app.
    # (Terraform Apply treats it as its own input; see wait-for-terraform-boundary.sh.)
    infra/observability/*) ;;
    *) runtime=true ;;
  esac
done
echo "runtime=${runtime}"
