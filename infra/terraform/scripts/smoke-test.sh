#!/usr/bin/env bash

set -euo pipefail

base_url="${1:?base URL is required}"
base_url="${base_url%/}"

case "${base_url}" in
  http://*|https://*) ;;
  *)
    echo "Smoke base URL must use http or https." >&2
    exit 1
    ;;
esac

command -v curl >/dev/null 2>&1 || {
  echo "curl is required." >&2
  exit 1
}
command -v jq >/dev/null 2>&1 || {
  echo "jq is required." >&2
  exit 1
}

response_file="$(mktemp /tmp/moimyeon-smoke-response.XXXXXX)"
trap 'rm -f "${response_file}"' EXIT

check_json() {
  local label="$1"
  local path="$2"
  local filter="$3"
  local attempt

  for attempt in 1 2 3; do
    if curl --fail --silent --show-error \
      --connect-timeout 3 \
      --max-time 5 \
      --output "${response_file}" \
      "${base_url}${path}" && jq -e "${filter}" "${response_file}" >/dev/null; then
      echo "Smoke passed: ${label}."
      return 0
    fi

    if [ "${attempt}" -lt 3 ]; then
      sleep $((attempt * 2))
    fi
  done

  echo "Smoke failed after 3 attempts: ${label} (${path})." >&2
  return 1
}

check_json "readiness" "/actuator/health/readiness" '.status == "UP"'
check_json "public terms read" "/v1/terms" '.result == "SUCCESS" and (.data.terms | type == "array")'
