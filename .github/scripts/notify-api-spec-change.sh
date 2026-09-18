#!/usr/bin/env bash

set -euo pipefail

diff_file="${API_SPEC_DIFF_FILE:?API_SPEC_DIFF_FILE is required}"
branch="${API_SPEC_BRANCH:?API_SPEC_BRANCH is required}"
sha="${API_SPEC_SHA:?API_SPEC_SHA is required}"
actor="${API_SPEC_ACTOR:?API_SPEC_ACTOR is required}"
run_url="${API_SPEC_RUN_URL:?API_SPEC_RUN_URL is required}"

if [ ! -s "${diff_file}" ]; then
  echo "API 스펙 변경이 없어 Slack 알림을 건너뛴다."
  exit 0
fi

if [ -z "${SLACK_WEBHOOK_URL:-}" ]; then
  echo "::warning::SLACK_API_SPEC_WEBHOOK_URL이 없어 API 스펙 변경 알림을 건너뛴다."
  exit 0
fi

changes_json="$(jq -Rn '
  [
    inputs
    | select(length > 0)
    | split("\t")
    | select(length == 3)
    | {kind: .[0], method: .[1], path: .[2]}
  ]
' < "${diff_file}")"
change_count="$(jq 'length' <<< "${changes_json}")"

if [ "${change_count}" -eq 0 ]; then
  echo "유효한 API 스펙 변경 항목이 없어 Slack 알림을 건너뛴다."
  exit 0
fi

max_items=15
change_list="$(jq -r --argjson max_items "${max_items}" '
  def escape_slack: gsub("&"; "&amp;") | gsub("<"; "&lt;") | gsub(">"; "&gt;");
  def display_path:
    escape_slack
    | if length > 160 then .[0:159] + "…" else . end;
  def icon:
    if . == "ADDED" then "➕"
    elif . == "REMOVED" then "➖"
    else "✏️"
    end;
  .[0:$max_items]
  | map("\(.kind | icon) `\(.method)` \(.path | display_path)")
  | join("\n")
' <<< "${changes_json}")"

if [ "${change_count}" -gt "${max_items}" ]; then
  omitted_count="$((change_count - max_items))"
  change_list="${change_list}
… 외 ${omitted_count}개"
fi

payload="$(jq -n \
  --arg branch "${branch}" \
  --arg sha "${sha}" \
  --arg actor "${actor}" \
  --arg run_url "${run_url}" \
  --arg change_count "${change_count}" \
  --arg change_list "${change_list}" '
  {
    text: ("[" + $branch + "] API 스펙 변경 " + $change_count + "개 (" + $sha[0:12] + ")"),
    blocks: [
      {
        type: "section",
        text: {
          type: "mrkdwn",
          text: ("*API 스펙 변경*: " + $change_count + "개")
        }
      },
      {
        type: "section",
        fields: [
          {type: "mrkdwn", text: ("*Branch*\n`" + $branch + "`")},
          {type: "mrkdwn", text: ("*SHA*\n`" + $sha[0:12] + "`")},
          {type: "mrkdwn", text: ("*Actor*\n" + $actor)}
        ]
      },
      {
        type: "section",
        text: {type: "mrkdwn", text: $change_list}
      },
      {
        type: "section",
        text: {type: "mrkdwn", text: ("<" + $run_url + "|GitHub Actions에서 확인>")}
      }
    ]
  }
')"

curl --fail --silent --show-error \
  --connect-timeout 3 \
  --max-time 10 \
  --header "Content-Type: application/json" \
  --data "${payload}" \
  "${SLACK_WEBHOOK_URL}" >/dev/null
