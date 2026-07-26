#!/usr/bin/env bash
#
# EC2 에서 ECR 이미지를 pull 해 컨테이너로 띄운다. (Session Manager 세션 안에서 실행)
#
# 사용법 (root 필요 → sudo):
#   sudo bash scripts/deploy.sh sha-517f3dad0940
#
# 사전 조건:
#   - docker, aws CLI 설치
#   - EC2 IAM Role 에 ECR Pull 권한
#   - 시크릿 env 파일: ENV_FILE(기본 /opt/moimyeon/app.env), chmod 600
#     (repo 의 .env.example 을 복사해 실제 값으로 채운다. git 에 올리지 않는다.)
#
# 환경변수(선택):
#   AWS_REGION     (기본 ap-northeast-2)
#   ECR_REPO_NAME  (기본 moimyeon/backend)
#   CONTAINER_NAME (기본 moimyeon-backend)
#   HOST_PORT      (기본 8080)
#   ENV_FILE       (기본 /opt/moimyeon/app.env)
#
set -euo pipefail

AWS_REGION="${AWS_REGION:-ap-northeast-2}"
ECR_REPO_NAME="${ECR_REPO_NAME:-moimyeon/backend}"
CONTAINER_NAME="${CONTAINER_NAME:-moimyeon-backend}"
HOST_PORT="${HOST_PORT:-8080}"
ENV_FILE="${ENV_FILE:-/opt/moimyeon/app.env}"

IMAGE_TAG="${1:-${IMAGE_TAG:-}}"
if [[ -z "${IMAGE_TAG}" ]]; then
  echo "usage: sudo bash $0 <image-tag>   예) sudo bash $0 sha-517f3dad0940" >&2
  exit 1
fi
if [[ ! -f "${ENV_FILE}" ]]; then
  echo "ERROR: env 파일이 없다: ${ENV_FILE}" >&2
  echo "  .env.example 를 참고해 만들어라:  sudo mkdir -p \$(dirname ${ENV_FILE}) && sudo vi ${ENV_FILE} && sudo chmod 600 ${ENV_FILE}" >&2
  exit 1
fi

AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
IMAGE="${ECR_REGISTRY}/${ECR_REPO_NAME}:${IMAGE_TAG}"

echo "==> ECR 로그인"
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

echo "==> pull ${IMAGE}"
docker pull "${IMAGE}"

echo "==> 기존 컨테이너 정리"
docker rm -f "${CONTAINER_NAME}" 2>/dev/null || true

echo "==> run"
docker run -d --name "${CONTAINER_NAME}" \
  --restart unless-stopped \
  -p "${HOST_PORT}:8080" \
  --env-file "${ENV_FILE}" \
  "${IMAGE}"

echo "==> health 대기 (최대 60s)"
for i in $(seq 1 30); do
  if curl -fs "http://localhost:${HOST_PORT}/actuator/health" >/dev/null 2>&1; then
    echo "==> UP: $(curl -s http://localhost:${HOST_PORT}/actuator/health)"
    echo "배포 완료: ${IMAGE}"
    exit 0
  fi
  if [[ -z "$(docker ps -q -f name="^${CONTAINER_NAME}$")" ]]; then
    echo "ERROR: 컨테이너가 조기 종료됨. 로그:" >&2
    docker logs --tail 60 "${CONTAINER_NAME}" >&2 || true
    exit 1
  fi
  sleep 2
done

echo "ERROR: health check 타임아웃. 최근 로그:" >&2
docker logs --tail 60 "${CONTAINER_NAME}" >&2 || true
exit 1
