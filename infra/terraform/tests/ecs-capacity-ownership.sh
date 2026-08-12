#!/usr/bin/env bash
set -euo pipefail

TERRAFORM_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ECS_MODULE="${TERRAFORM_ROOT}/modules/moimyeon-environment/ecs.tf"
DEV_ENV="${TERRAFORM_ROOT}/envs/dev/main.tf"

if grep -Rqs "ecs_desired_capacity" \
  "${TERRAFORM_ROOT}/modules/moimyeon-environment" \
  "${TERRAFORM_ROOT}/envs/dev" \
  "${TERRAFORM_ROOT}/envs/live"; then
  echo "ECS managed scaling과 Terraform이 ASG desired capacity를 함께 소유하면 안 된다." >&2
  exit 1
fi

if grep -Eq '^[[:space:]]*desired_capacity[[:space:]]*=' "${ECS_MODULE}"; then
  echo "ASG desired_capacity는 ECS Capacity Provider가 단독으로 관리해야 한다." >&2
  exit 1
fi

if ! grep -Eq '^[[:space:]]*target_capacity[[:space:]]*=[[:space:]]*100[[:space:]]*$' "${ECS_MODULE}"; then
  echo "기본 ECS 용량은 예비 인스턴스 없이 target_capacity 100으로 운영한다." >&2
  exit 1
fi

dev_max_size="$(sed -En 's/^[[:space:]]*ecs_max_size[[:space:]]*=[[:space:]]*([0-9]+)[[:space:]]*$/\1/p' "${DEV_ENV}")"

if [[ ! "${dev_max_size}" =~ ^[0-9]+$ ]] || ((dev_max_size < 4)); then
  echo "dev ECS는 API 롤링 교체와 Worker, Redis를 함께 수용하도록 ecs_max_size를 4 이상으로 유지해야 한다." >&2
  exit 1
fi
