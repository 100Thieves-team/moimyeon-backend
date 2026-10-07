#!/usr/bin/env bash

set -euo pipefail

# A missing webhook or a failed send never fails the deployment itself, but it
# must stay visible: annotate the run and note it in the step summary (MOI-490).
report_unsent() {
  local title="$1"
  local message="$2"

  echo "::warning title=${title}::${message}"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    echo "- ${title}: ${message}" >> "${GITHUB_STEP_SUMMARY}"
  fi
}

if [ -z "${SLACK_WEBHOOK_URL:-}" ]; then
  report_unsent "Slack notification not sent" \
    "Slack webhook is not configured; skipping deployment notification."
  exit 0
fi

deploy_environment="${DEPLOY_ENVIRONMENT:?DEPLOY_ENVIRONMENT is required}"
deploy_kind="${DEPLOY_KIND:?DEPLOY_KIND is required}"
deploy_outcome="${DEPLOY_OUTCOME:?DEPLOY_OUTCOME is required}"
deploy_sha="${DEPLOY_SHA:-unknown}"
deploy_actor="${DEPLOY_ACTOR:-unknown}"
deploy_run_url="${DEPLOY_RUN_URL:?DEPLOY_RUN_URL is required}"
api_result="${API_RESULT:-unknown}"
worker_result="${WORKER_RESULT:-unknown}"
shared_result="${SHARED_RESULT:-unknown}"
dev_result="${DEV_RESULT:-unknown}"
deploy_reason="${DEPLOY_REASON:-not-provided}"
# Optional: shown only when set, so existing callers keep their message.
deploy_stage="${DEPLOY_STAGE:-}"
image_source="${IMAGE_SOURCE:-}"

payload="$(jq -n \
  --arg environment "${deploy_environment}" \
  --arg kind "${deploy_kind}" \
  --arg outcome "${deploy_outcome}" \
  --arg sha "${deploy_sha}" \
  --arg actor "${deploy_actor}" \
  --arg api "${api_result}" \
  --arg worker "${worker_result}" \
  --arg shared "${shared_result}" \
  --arg dev "${dev_result}" \
  --arg reason "${deploy_reason}" \
  --arg stage "${deploy_stage}" \
  --arg image "${image_source}" \
  --arg run_url "${deploy_run_url}" '
  def link: {type: "section", text: {type: "mrkdwn", text: ("<" + $run_url + "|GitHub Actions run>")}};

  {text: ("[" + $environment + "] " + $kind + " " + $outcome + " (" + $sha[0:12] + ")")}
  + if $kind == "deployment" and $outcome == "skipped" then
      # A superseded dev deploy is not a result; keep it to one line plus the
      # run link. Other kinds (live rollback) keep their full message.
      {
        blocks: [
          {
            type: "section",
            text: {
              type: "mrkdwn",
              text: ("*" + $environment + " " + $kind + "*: skipped `" + $sha[0:12] + "` (" + $reason + ")")
            }
          },
          link
        ]
      }
    else
      {
        blocks: [
          {
            type: "section",
            text: {
              type: "mrkdwn",
              text: ("*" + $environment + " " + $kind + "*: " + $outcome)
            }
          },
          {
            type: "section",
            fields: (
              [
                {type: "mrkdwn", text: ("*SHA*\n`" + $sha + "`")},
                {type: "mrkdwn", text: ("*Actor*\n" + $actor)}
              ]
              + if $kind == "terraform" then
                  [
                    {type: "mrkdwn", text: ("*Shared*\n" + $shared)},
                    {type: "mrkdwn", text: ("*Dev*\n" + $dev)}
                  ]
                else
                  [
                    {type: "mrkdwn", text: ("*Core API*\n" + $api)},
                    {type: "mrkdwn", text: ("*Worker*\n" + $worker)}
                  ]
                end
              + (if $stage == "" then [] else [{type: "mrkdwn", text: ("*Stage*\n" + $stage)}] end)
              + [{type: "mrkdwn", text: ("*Reason*\n" + $reason)}]
              + (if $image == "" then [] else [{type: "mrkdwn", text: ("*Image*\n" + $image)}] end)
            )
          },
          link
        ]
      }
    end')"

if ! curl --fail --silent --show-error \
  --connect-timeout 3 \
  --max-time 10 \
  --header "Content-Type: application/json" \
  --data "${payload}" \
  "${SLACK_WEBHOOK_URL}" >/dev/null; then
  report_unsent "Slack notification failed" \
    "Sending the ${deploy_environment} ${deploy_kind} ${deploy_outcome} notification failed."
  exit 1
fi
