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
  echo "ECS 용량은 target_capacity 100으로 운영한다. dev 예비 인스턴스는 ecs_min_size로만 둔다." >&2
  exit 1
fi

dev_max_size="$(sed -En 's/^[[:space:]]*ecs_max_size[[:space:]]*=[[:space:]]*([0-9]+)[[:space:]]*$/\1/p' "${DEV_ENV}")"

if [[ ! "${dev_max_size}" =~ ^[0-9]+$ ]] || ((dev_max_size < 4)); then
  echo "dev ECS는 API 롤링 교체와 Worker, Redis를 함께 수용하도록 ecs_max_size를 4 이상으로 유지해야 한다." >&2
  exit 1
fi

dev_min_size="$(sed -En 's/^[[:space:]]*ecs_min_size[[:space:]]*=[[:space:]]*([0-9]+)[[:space:]]*$/\1/p' "${DEV_ENV}")"

# MOI-565: API 태스크가 t3.small 한 대를 통째로 쓴다. 평소 3대(API·Worker·Redis)에
# 빈 1대를 상시 두어 롤링 교체가 새 인스턴스 기동을 기다리지 않게 한다.
if [[ ! "${dev_min_size}" =~ ^[0-9]+$ ]] || ((dev_min_size < 4)); then
  echo "dev ECS는 API 롤링 교체용 빈 인스턴스를 상시 두도록 ecs_min_size를 4 이상으로 유지해야 한다." >&2
  exit 1
fi

if ((dev_max_size <= dev_min_size)); then
  echo "dev ECS는 교체 중 임시 인스턴스를 위해 ecs_max_size가 ecs_min_size보다 커야 한다." >&2
  exit 1
fi
