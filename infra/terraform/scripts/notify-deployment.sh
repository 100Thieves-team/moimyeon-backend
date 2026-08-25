#!/usr/bin/env bash

set -euo pipefail

if [ -z "${SLACK_WEBHOOK_URL:-}" ]; then
  echo "Slack webhook is not configured; skipping deployment notification."
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
deploy_reason="${DEPLOY_REASON:-not-provided}"

payload="$(jq -n \
  --arg environment "${deploy_environment}" \
  --arg kind "${deploy_kind}" \
  --arg outcome "${deploy_outcome}" \
  --arg sha "${deploy_sha}" \
  --arg actor "${deploy_actor}" \
  --arg api "${api_result}" \
  --arg worker "${worker_result}" \
  --arg reason "${deploy_reason}" \
  --arg run_url "${deploy_run_url}" '
  {
    text: ("[" + $environment + "] " + $kind + " " + $outcome + " (" + $sha[0:12] + ")"),
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
        fields: [
          {type: "mrkdwn", text: ("*SHA*\n`" + $sha + "`")},
          {type: "mrkdwn", text: ("*Actor*\n" + $actor)},
          {type: "mrkdwn", text: ("*Core API*\n" + $api)},
          {type: "mrkdwn", text: ("*Worker*\n" + $worker)},
          {type: "mrkdwn", text: ("*Reason*\n" + $reason)}
        ]
      },
      {
        type: "section",
        text: {
          type: "mrkdwn",
          text: ("<" + $run_url + "|GitHub Actions run>")
        }
      }
    ]
  }')"

curl --fail --silent --show-error \
  --connect-timeout 3 \
  --max-time 10 \
  --header "Content-Type: application/json" \
  --data "${payload}" \
  "${SLACK_WEBHOOK_URL}" >/dev/null
