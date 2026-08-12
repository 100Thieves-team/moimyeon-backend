#!/usr/bin/env bash
set -euo pipefail

TERRAFORM_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ECS_MODULE="${TERRAFORM_ROOT}/modules/moimyeon-environment/ecs.tf"

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
