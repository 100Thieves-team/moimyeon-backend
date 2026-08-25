#!/usr/bin/env bash

set -euo pipefail

source_image="${1:?source image reference is required}"
target_image="${2:?target image tag is required}"

command -v docker >/dev/null 2>&1 || {
  echo "docker with buildx is required." >&2
  exit 1
}

image_digest() {
  docker buildx imagetools inspect "$1" --format '{{.Manifest.Digest}}'
}

source_digest="$(image_digest "${source_image}")"
if [[ ! "${source_digest}" =~ ^sha256:[0-9a-f]{64}$ ]]; then
  echo "Source image did not resolve to a sha256 digest: ${source_image}." >&2
  exit 1
fi

target_digest=""
if target_digest="$(image_digest "${target_image}" 2>/dev/null)"; then
  if [ "${target_digest}" != "${source_digest}" ]; then
    echo "Target tag already points at a different digest: ${target_image}." >&2
    exit 1
  fi
  echo "Target image already exists with the source digest: ${target_image}." >&2
else
  docker buildx imagetools create --tag "${target_image}" "${source_image}"
  target_digest="$(image_digest "${target_image}")"
fi

if [ "${target_digest}" != "${source_digest}" ]; then
  echo "Image digest changed during promotion: ${source_digest} -> ${target_digest}." >&2
  exit 1
fi

target_repository="${target_image%:*}"
echo "${target_repository}@${target_digest}"
