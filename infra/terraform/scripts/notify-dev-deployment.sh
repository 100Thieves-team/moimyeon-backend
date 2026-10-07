#!/usr/bin/env bash

# Turns the dev deploy job's step results into the notification fields, then
# sends it with notify-deployment.sh (MOI-490). Every input may be empty: the
# job can stop before the step that sets it.

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

current="${CURRENT:-}"
latest_sha="${LATEST_SHA:-}"
api_result="${API_RESULT:-}"
api_build_result="${API_BUILD_RESULT:-}"
api_candidate="${API_CANDIDATE:-}"
api_image_exists="${API_IMAGE_EXISTS:-}"
worker_step_result="${WORKER_STEP_RESULT:-}"
worker_enabled="${WORKER_ENABLED:-}"
worker_build_succeeded="${WORKER_BUILD_SUCCEEDED:-}"
worker_candidate="${WORKER_CANDIDATE:-}"
worker_image="${WORKER_IMAGE:-}"
candidate_tag="${CANDIDATE_TAG:-}"
repository_url="${REPOSITORY_URL:-}"

# A run superseded by a newer runtime revision is not a result.
if [ "${current}" = false ]; then
  export DEPLOY_OUTCOME=skipped
  export DEPLOY_REASON="superseded by ${latest_sha::12}, which deploys instead"
fi

# The Worker deploy step is skipped, not failed, when its image build failed
# after a stable API; report the actual cause.
worker_result="${worker_step_result}"
if [ "${worker_enabled}" = false ]; then
  worker_result=disabled
elif [ "${worker_enabled}" = true ] && [ "${api_result}" = success ] \
  && [ "${worker_build_succeeded}" != true ]; then
  worker_result=image-build-failed
fi
export WORKER_RESULT="${worker_result}"

pr_ci_run() {
  local run="${candidate_tag#*-run-}"
  echo "PR CI <${repository_url}/actions/runs/${run%-*}/attempts/${run##*-}|run ${run%-*} attempt ${run##*-}>"
}

# Where the deployed images came from: the PR CI run that built them, an image
# an earlier attempt left, or this deploy run.
image_source=""
if [ -n "${api_image_exists}" ]; then
  if [ "${api_candidate}" = promoted ]; then
    api_source="$(pr_ci_run)"
  elif [ "${api_image_exists}" = true ]; then
    api_source="existing deploy image"
  elif [ "${api_build_result}" = success ]; then
    api_source="built during deploy"
  else
    api_source="build ${api_build_result:-not finished}"
  fi
  image_source="Core API: ${api_source}"

  if [ "${worker_enabled}" = true ] && [ "${worker_build_succeeded}" = true ]; then
    if [ "${worker_candidate}" = promoted ]; then
      worker_source="$(pr_ci_run)"
    elif [ "${worker_image}" = built ]; then
      worker_source="built during deploy"
    else
      worker_source="existing deploy image"
    fi
    image_source="${image_source}"$'\n'"Worker: ${worker_source}"
  fi
fi
export IMAGE_SOURCE="${image_source}"

exec bash "${script_dir}/notify-deployment.sh"
