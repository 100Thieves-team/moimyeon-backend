#!/usr/bin/env bash

# Reads changed paths (one per line) on stdin and prints runtime=true when any
# of them can change what runs, runtime=false when only documentation changed.
# Same rule as the dev deploy and live promotion gates (DR-005); MOI-592 moves
# those gates onto this script.

set -euo pipefail

runtime=false
while IFS= read -r path; do
  [ -n "${path}" ] || continue
  case "${path}" in
    docs/*|*.md|*.mdx) ;;
    *) runtime=true ;;
  esac
done
echo "runtime=${runtime}"
