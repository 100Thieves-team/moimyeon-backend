#!/usr/bin/env bash
set -euo pipefail

obs_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
work_dir=$(mktemp -d /tmp/moimyeon-monitoring-smoke.XXXXXX)
project="monitoring-smoke-$(date +%s)-$$"
export MONITORING_DATA_DIR="$work_dir/data"
export GRAFANA_ADMIN_PASSWORD_FILE="$work_dir/grafana-password"
export OTLP_BIND_ADDRESS=127.0.0.1
export OTLP_HTTP_PORT="${OTLP_HTTP_PORT:-0}"
export COLLECTOR_HEALTH_PORT="${COLLECTOR_HEALTH_PORT:-0}"
export PROMETHEUS_PORT="${PROMETHEUS_PORT:-0}"
export GRAFANA_PORT="${GRAFANA_PORT:-0}"
mkdir -p "$MONITORING_DATA_DIR/prometheus" "$MONITORING_DATA_DIR/grafana"
# Throwaway synthetic credential/data, never a dev SSM value.
printf '%s\n' 'local-smoke-only-password' > "$GRAFANA_ADMIN_PASSWORD_FILE"
chmod 0444 "$GRAFANA_ADMIN_PASSWORD_FILE"
chmod 0755 "$work_dir"
chmod 0777 "$MONITORING_DATA_DIR/prometheus" "$MONITORING_DATA_DIR/grafana"

compose() { docker compose -p "$project" -f "$obs_dir/compose.yaml" "$@"; }
cleanup() {
  status=$?
  if [ "$status" -ne 0 ]; then compose logs --no-color --tail 80; fi
  compose down --timeout 15 >/dev/null
  echo "Synthetic test artifacts: $work_dir"
}
trap cleanup EXIT
compose config --quiet
compose run --rm --no-deps --entrypoint /bin/promtool prometheus check config /etc/prometheus/prometheus.yaml
compose run --rm --no-deps otel-collector validate --config=/etc/otelcol/config.yaml
compose up -d --wait --wait-timeout 180
otlp_port=$(compose port otel-collector 4318 | awk -F: '{print $NF}')
collector_health_port=$(compose port otel-collector 13133 | awk -F: '{print $NF}')
prometheus_port=$(compose port prometheus 9090 | awk -F: '{print $NF}')
grafana_port=$(compose port grafana 3000 | awk -F: '{print $NF}')
curl --fail --silent --show-error "http://127.0.0.1:$collector_health_port/" >/dev/null
curl --fail --silent --show-error "http://127.0.0.1:$grafana_port/api/health" | jq -e '.database == "ok"' >/dev/null
http_code=$(curl --silent --output /dev/null --write-out '%{http_code}' "http://127.0.0.1:$grafana_port/api/datasources")
[ "$http_code" = 401 ] || { echo "Grafana anonymous datasource access unexpectedly returned $http_code" >&2; exit 1; }

now_seconds=$(date +%s)
now_ns="${now_seconds}000000000"
for instance in first second; do
  jq -n --arg instance "$instance" --arg time "$now_ns" --argjson seconds "$now_seconds" '{resourceMetrics:[{resource:{attributes:[
    {key:"service.name",value:{stringValue:"monitoring-smoke"}},
    {key:"service.instance.id",value:{stringValue:$instance}},
    {key:"service.version",value:{stringValue:"smoke"}},
    {key:"deployment.environment.name",value:{stringValue:"dev"}}
  ]},scopeMetrics:[{scope:{name:"smoke"},metrics:[
    {name:"monitoring.smoke.value",gauge:{dataPoints:[{timeUnixNano:$time,asDouble:42}]}},
    {name:"observability.heartbeat",unit:"s",gauge:{dataPoints:[{timeUnixNano:$time,asDouble:$seconds}]}},
    {name:"http.server.requests",unit:"s",histogram:{aggregationTemporality:2,dataPoints:[{
      timeUnixNano:$time,startTimeUnixNano:"1000000000",count:"1",sum:0.25,
      explicitBounds:[0.1,0.3],bucketCounts:["0","1","0"],attributes:[
        {key:"method",value:{stringValue:"GET"}},
        {key:"status",value:{stringValue:"200"}},
        {key:"uri",value:{stringValue:"/monitoring-smoke"}}
      ]
    }]}}
  ]}]}]}' \
    | curl --fail --silent --show-error -H 'Content-Type: application/json' --data-binary @- "http://127.0.0.1:$otlp_port/v1/metrics" >/dev/null
done

query='monitoring_smoke_value{service_name="monitoring-smoke",deployment_environment_name="dev"}'
found=false
for attempt in $(seq 1 15); do
  if curl --fail --silent --show-error --get --data-urlencode "query=$query" "http://127.0.0.1:$prometheus_port/api/v1/query" \
    | jq -e '.status == "success" and (.data.result | length == 2) and ([.data.result[].metric.service_instance_id] | sort == ["first","second"]) and all(.data.result[]; .value[1] == "42")' >/dev/null; then
    found=true
    break
  fi
  sleep 3
done
[ "$found" = true ] || { echo 'Two independent OTLP instance series did not reach Prometheus.' >&2; exit 1; }

prom_query() {
  curl --fail --silent --show-error --get --data-urlencode "query=$1" "http://127.0.0.1:$prometheus_port/api/v1/query"
}
prom_query 'sum(http_server_requests_seconds_sum{service_name="monitoring-smoke"})' \
  | jq -e '.status == "success" and .data.result[0].value[1] == "0.5"' >/dev/null
prom_query 'sum(http_server_requests_seconds_bucket{service_name="monitoring-smoke",le="0.3"})' \
  | jq -e '.status == "success" and .data.result[0].value[1] == "2"' >/dev/null
prom_query 'count(observability_heartbeat_seconds{service_name="monitoring-smoke"})' \
  | jq -e '.status == "success" and .data.result[0].value[1] == "2"' >/dev/null

# Validate every real provisioned dashboard expression using this Prometheus
# version. A syntactically valid empty result is not proof of real dev traffic.
while IFS= read -r expression; do
  prom_query "$expression" | jq -e '.status == "success"' >/dev/null
done < <(jq -r '.panels[].targets[]?.expr' "$obs_dir/grafana/dashboards/dev-overview.json")
echo 'PASS: configuration, Grafana auth, per-instance OTLP, seconds histogram and dashboard PromQL.'

# Stop sending but keep the receiver healthy. Expired app samples must vanish
# instead of a healthy Collector making a stopped application look healthy.
expired=false
for attempt in $(seq 1 40); do
  if prom_query 'absent(observability_heartbeat_seconds{service_name="monitoring-smoke"})' \
    | jq -e '.status == "success" and .data.result[0].value[1] == "1"' >/dev/null; then
    expired=true
    break
  fi
  sleep 3
done
[ "$expired" = true ] || { echo 'Stopped OTLP heartbeat did not expire.' >&2; exit 1; }
prom_query 'up{job="otel-collector"}' \
  | jq -e '.status == "success" and .data.result[0].value[1] == "1"' >/dev/null
echo 'PASS: stopped application heartbeat expires while Collector remains healthy.'
