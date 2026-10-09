#!/usr/bin/env bash

# Builds the executable bootJars on the runner and stages them under fixed
# names as the Docker build context (MOI-588). The runner keeps its Gradle
# cache between runs; a Gradle build inside Docker started empty every time.
#
# Usage: build-image-jars.sh <output-dir> <core-api|core-worker>...

set -euo pipefail

output_dir="${1:?output directory is required}"
shift
[ "$#" -gt 0 ] || {
  echo "at least one of core-api, core-worker is required." >&2
  exit 1
}

tasks=()
for module in "$@"; do
  case "${module}" in
    core-api|core-worker) tasks+=(":core:${module}:bootJar") ;;
    *)
      echo "unknown image module: ${module}" >&2
      exit 1
      ;;
  esac
done

# Gradle runs build scripts and plugins from outside dependencies. Keep AWS
# credentials, the OIDC token request and the ECR login out of its reach.
docker_config="$(mktemp -d)"
env -u AWS_ACCESS_KEY_ID -u AWS_SECRET_ACCESS_KEY -u AWS_SESSION_TOKEN \
  -u ACTIONS_ID_TOKEN_REQUEST_TOKEN -u ACTIONS_ID_TOKEN_REQUEST_URL \
  DOCKER_CONFIG="${docker_config}" \
  ./gradlew "${tasks[@]}"
rm -rf "${docker_config}"

rm -rf "${output_dir}"
mkdir -p "${output_dir}"
for module in "$@"; do
  jars=()
  while IFS= read -r jar; do
    jars+=("${jar}")
  done < <(find "core/${module}/build/libs" -maxdepth 1 -name '*.jar' ! -name '*-plain.jar')
  if [ "${#jars[@]}" -ne 1 ]; then
    echo "expected one ${module} bootJar, found ${#jars[@]}." >&2
    exit 1
  fi
  # The Dockerfile copies a fixed name and renames it to app.jar before layer
  # extraction (docs/knowledge/operations.md, 2026-08-25).
  cp "${jars[0]}" "${output_dir}/${module}.jar"
done
