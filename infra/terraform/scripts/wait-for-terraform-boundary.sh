#!/usr/bin/env bash

# Waits until the applied dev Terraform revision covers deploy_sha's Terraform
# source (MOI-565). The Terraform Apply workflow records that revision in SSM
# after each successful dev apply or no-op sync. App commits that leave
# infra/terraform unchanged since then deploy immediately; a deploy behind an
# unapplied Terraform change waits for it instead of racing it.

set -euo pipefail

deploy_sha="${1:?deploy SHA is required}"
parameter_name="${2:?applied SHA parameter name is required}"
wait_seconds="${TERRAFORM_BOUNDARY_WAIT_SECONDS:-5400}"
poll_seconds="${TERRAFORM_BOUNDARY_POLL_SECONDS:-15}"

[[ "${deploy_sha}" =~ ^[0-9a-f]{40}$ ]] || exit 1
[[ "${parameter_name}" =~ ^/[A-Za-z0-9_./-]+$ ]] || exit 1
[[ "${wait_seconds}" =~ ^[1-9][0-9]*$ ]] || exit 1
[[ "${poll_seconds}" =~ ^[1-9][0-9]*$ ]] || exit 1

# Same scope the Terraform Apply workflow uses to decide whether to run.
terraform_paths=(infra/terraform ':(exclude)infra/terraform/tests' ':(exclude)infra/terraform/README.md')

deadline=$((SECONDS + wait_seconds))
fetched=false
while [ "${SECONDS}" -lt "${deadline}" ]; do
  applied_sha="$(aws ssm get-parameter \
    --name "${parameter_name}" \
    --query 'Parameter.Value' \
    --output text 2>/dev/null || true)"

  if [[ "${applied_sha}" =~ ^[0-9a-f]{40}$ ]]; then
    if ! git cat-file -e "${applied_sha}^{commit}" 2>/dev/null && [ "${fetched}" = false ]; then
      git fetch --no-tags --quiet origin "+refs/heads/dev:refs/remotes/origin/dev"
      fetched=true
    fi
    if git cat-file -e "${applied_sha}^{commit}" 2>/dev/null; then
      if git diff --quiet --no-renames "${applied_sha}" "${deploy_sha}" -- "${terraform_paths[@]}"; then
        echo "Terraform source matches applied dev revision ${applied_sha}."
        exit 0
      fi
      if git merge-base --is-ancestor "${deploy_sha}" "${applied_sha}"; then
        echo "Newer dev Terraform revision ${applied_sha} is already applied."
        exit 0
      fi
      echo "Waiting for Terraform Apply to cover ${deploy_sha} (applied: ${applied_sha})."
    fi
  else
    echo "Waiting for the first applied dev Terraform revision in ${parameter_name}."
  fi

  sleep "${poll_seconds}"
  fetched=false
done

echo "Timed out waiting for Terraform Apply to cover ${deploy_sha}." >&2
exit 1
