#!/usr/bin/env bash

# The inputs of a deployment image that can change what it runs (MOI-590):
# the boot jar and the image definition. Images carry the value as a label so
# a deploy can tell whether the running Worker already has this commit's code.
#
#   runtime-input.sh hash <boot jar>   prints the input hash
#   runtime-input.sh read <image>      prints the image's label, or nothing
#
# The JRE base image and the AOT cache are left out on purpose: they change
# without a code change, and a Worker restart for them is not needed.

set -euo pipefail

LABEL=org.moimyeon.runtime-input

case "${1:-}" in
  hash)
    jar="${2:?boot jar path is required}"
    root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
    {
      sha256sum < "${jar}"
      sha256sum < "${root}/Dockerfile"
      sha256sum < "${root}/Dockerfile.dockerignore"
    } | sha256sum | cut -d' ' -f1
    ;;
  read)
    image="${2:?image reference is required}"
    # A single-platform image prints its config; an index prints one per platform.
    docker buildx imagetools inspect "${image}" --format '{{json .Image}}' \
      | jq -r --arg label "${LABEL}" '
        (if has("config") then . else ([to_entries[] | select(.key | startswith("linux/amd64"))] | first | .value) end)
        | .config.Labels[$label] // empty'
    ;;
  *)
    echo "usage: runtime-input.sh hash <jar> | read <image>" >&2
    exit 1
    ;;
esac
