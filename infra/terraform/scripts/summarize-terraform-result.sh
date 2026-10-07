#!/usr/bin/env bash

# Summarizes the dev Terraform Apply jobs for the notification (MOI-490) and
# writes notify/outcome/stage/shared/dev/reason to GITHUB_OUTPUT.
#
# The plan, apply and variable sync reusable workflows end a superseded run with
# current=false and a failed job. That is a routine non-result, so it is checked
# before any failure: it stays in the step summary unless something was already
# applied, which the newer run would then see as no change.

set -euo pipefail

output="${GITHUB_OUTPUT:?GITHUB_OUTPUT is required}"
summary="${GITHUB_STEP_SUMMARY:-/dev/null}"

plan_shared_result="${PLAN_SHARED_RESULT:-}"
plan_shared_current="${PLAN_SHARED_CURRENT:-}"
shared_apply_required="${SHARED_APPLY_REQUIRED:-}"
apply_shared_result="${APPLY_SHARED_RESULT:-}"
apply_shared_current="${APPLY_SHARED_CURRENT:-}"
plan_dev_result="${PLAN_DEV_RESULT:-}"
plan_dev_current="${PLAN_DEV_CURRENT:-}"
dev_apply_required="${DEV_APPLY_REQUIRED:-}"
apply_dev_result="${APPLY_DEV_RESULT:-}"
apply_dev_current="${APPLY_DEV_CURRENT:-}"
sync_dev_result="${SYNC_DEV_RESULT:-}"
sync_dev_current="${SYNC_DEV_CURRENT:-}"

environment_result() {
  local plan_result="$1"
  local plan_current="$2"
  local apply_required="$3"
  local apply_result="$4"
  local apply_current="$5"

  if [ "${plan_current}" = false ] || [ "${apply_current}" = false ]; then
    echo "superseded"
  elif [ "${plan_result}" != success ]; then
    echo "plan ${plan_result:-skipped}"
  elif [ "${apply_required}" = true ]; then
    if [ "${apply_result}" = success ]; then echo applied; else echo "apply ${apply_result:-skipped}"; fi
  else
    echo "no changes"
  fi
}

shared="$(environment_result "${plan_shared_result}" "${plan_shared_current}" \
  "${shared_apply_required}" "${apply_shared_result}" "${apply_shared_current}")"
dev="$(environment_result "${plan_dev_result}" "${plan_dev_current}" \
  "${dev_apply_required}" "${apply_dev_result}" "${apply_dev_current}")"

if [ "${shared}" = applied ] && [ "${dev}" = applied ]; then
  applied="Shared and dev"
elif [ "${shared}" = applied ]; then
  applied="Shared"
elif [ "${dev}" = applied ]; then
  applied="Dev"
else
  applied=""
fi

if [ "${shared}" = superseded ] || [ "${dev}" = superseded ] || [ "${sync_dev_current}" = false ]; then
  if [ -z "${applied}" ]; then
    echo "A newer dev revision applies Terraform instead; no notification." >> "${summary}"
    echo "notify=false" >> "${output}"
    exit 0
  fi
  outcome=success
  stage=""
  reason="${applied} changes applied; a newer dev revision finishes the rest"
else
  outcome=success
  stage=""
  for step in \
    "Shared plan=${plan_shared_result}" \
    "Shared apply=${apply_shared_result}" \
    "Dev plan=${plan_dev_result}" \
    "Dev apply=${apply_dev_result}" \
    "Dev variable sync=${sync_dev_result}"; do
    case "${step##*=}" in
      failure|cancelled)
        outcome="${step##*=}"
        stage="${step%=*}"
        break
        ;;
    esac
  done

  # A run cancelled between jobs leaves the next required job skipped rather
  # than cancelled; that must not read as "no changes".
  if [ "${outcome}" = success ]; then
    for step in \
      "Shared plan=${plan_shared_result}=true" \
      "Shared apply=${apply_shared_result}=${shared_apply_required}" \
      "Dev plan=${plan_dev_result}=true" \
      "Dev apply=${apply_dev_result}=${dev_apply_required}" \
      "Dev variable sync=${sync_dev_result}=true"; do
      required="${step##*=}"
      result="${step%=*}"
      result="${result##*=}"
      if [ "${required}" = true ] && [ "${result}" != success ]; then
        outcome=cancelled
        stage="${step%%=*}"
        break
      fi
    done
  fi

  # The sync records the applied revision for deploys before it syncs GitHub
  # variables, so a sync failure does not always hold deploys back.
  if [ "${stage}" = "Dev variable sync" ]; then
    impact="deploys behind this change wait only if the applied revision was not recorded; check the run"
  else
    impact="deploys behind this Terraform change wait and then time out"
  fi
  case "${outcome}" in
    failure) reason="${stage} failed; ${impact}" ;;
    cancelled) reason="${stage} was cancelled; ${impact}" ;;
    *)
      if [ -n "${applied}" ]; then
        reason="Terraform changes applied"
      else
        reason="No infrastructure changes to apply"
      fi
      ;;
  esac
fi

{
  echo "notify=true"
  echo "outcome=${outcome}"
  echo "stage=${stage}"
  echo "shared=${shared}"
  echo "dev=${dev}"
  echo "reason=${reason}"
} >> "${output}"
