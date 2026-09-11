#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077

region="${1:?AWS region required}"
volume_id="${2:?Retained EBS volume ID required}"
grafana_parameter="${3:?Grafana SSM parameter ARN required}"
mode="${4:?Expected prepare or run}"
case "$mode" in prepare|run) ;; *) exit 1 ;; esac
[[ "$volume_id" =~ ^vol-[0-9a-f]+$ ]] || exit 1
[[ "$region" =~ ^[a-z]{2}-[a-z]+-[0-9]+$ ]] || exit 1
[[ "$grafana_parameter" == arn:aws:ssm:*:parameter/moimyeon/dev/monitoring/GRAFANA_ADMIN_PASSWORD ]] || exit 1

data_dir=/var/lib/moimyeon-monitoring
if [ "$mode" = run ]; then
  # Docker has a Requires/After dependency on the prepare unit. Never allow
  # bind mounts to create an empty data directory on the disposable root disk.
  mountpoint -q "$data_dir" || { echo 'Monitoring data is not mounted.' >&2; exit 1; }
  export MONITORING_DATA_DIR="$data_dir"
  export GRAFANA_ADMIN_PASSWORD_FILE=/run/moimyeon-monitoring/grafana_admin_password
  [ -s "$GRAFANA_ADMIN_PASSWORD_FILE" ] || exit 1
  script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
  docker compose -f "$script_dir/compose.yaml" config --quiet
  docker compose -f "$script_dir/compose.yaml" pull --quiet
  docker compose -f "$script_dir/compose.yaml" up -d --wait --wait-timeout 180
  curl --fail --silent --show-error --max-time 5 http://127.0.0.1:13133/ >/dev/null
  echo 'Monitoring containers are ready; verify real API and Worker samples separately.'
  exit 0
fi

device_link="/dev/disk/by-id/nvme-Amazon_Elastic_Block_Store_${volume_id//-/}"
for attempt in $(seq 1 60); do
  [ -b "$device_link" ] && break
  sleep 2
done
[ -b "$device_link" ] || { echo 'Expected monitoring EBS attachment is not ready.' >&2; exit 1; }
device=$(readlink -f "$device_link")

install -d -m 0755 "$data_dir"
if mountpoint -q "$data_dir"; then
  mounted_device=$(findmnt -n -o SOURCE --target "$data_dir")
  [ "$(readlink -f "$mounted_device")" = "$device" ] || { echo 'Unexpected device at monitoring mountpoint.' >&2; exit 1; }
else
  filesystem=$(blkid -s TYPE -o value "$device" || true)
  if [ -z "$filesystem" ]; then
    # Only an entirely blank, exact-ID volume is eligible for first-use format.
    [ -z "$(wipefs --no-act "$device")" ] || { echo 'Unrecognized existing volume signature; refusing format.' >&2; exit 1; }
    [ "$(lsblk --noheadings --raw --output NAME "$device" | wc -l)" -eq 1 ] || { echo 'Partitioned volume; refusing format.' >&2; exit 1; }
    mkfs.xfs "$device"
    filesystem=xfs
  fi
  [ "$filesystem" = xfs ] || { echo 'Expected XFS monitoring data volume; refusing modification.' >&2; exit 1; }
  mount -o nodev,nosuid "$device" "$data_dir"
fi

# The service mounts the explicit retained volume on every boot, not a stale fstab device.
install -d -o 65534 -g 65534 -m 0750 "$data_dir/prometheus"
install -d -o 472 -g 0 -m 0750 "$data_dir/grafana"
install -d -m 0755 /run/moimyeon-monitoring
secret_temp=$(mktemp /run/moimyeon-monitoring/.grafana-password.XXXXXX)
trap 'test ! -e "$secret_temp" || unlink "$secret_temp"' EXIT
if ! aws ssm get-parameter --region "$region" --name "$grafana_parameter" --with-decryption \
  --query Parameter.Value --output text > "$secret_temp"; then
  echo 'Grafana credential preparation failed; check SSM name, IAM and network, then follow the runbook retry procedure.' >&2
  exit 1
fi
[ "$(wc -c < "$secret_temp")" -ge 13 ] || { echo 'Grafana admin password must be at least 12 characters.' >&2; exit 1; }
install -o 472 -g 0 -m 0400 "$secret_temp" /run/moimyeon-monitoring/grafana_admin_password
unlink "$secret_temp"
trap - EXIT

echo 'Retained monitoring storage and ephemeral credential are ready.'
