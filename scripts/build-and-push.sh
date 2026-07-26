#!/usr/bin/env bash
#
# core-api 이미지를 linux/amd64 로 빌드해 ECR 에 push 한다. (개발 PC / CI 에서 실행)
#
# 사용법:
#   ./scripts/build-and-push.sh
#
# 사전 조건:
#   - docker buildx, aws CLI (ECR push 권한 있는 자격증명)
#   - 워킹트리 clean (SHA 태그가 커밋과 일치해야 함)
#
# 환경변수(선택):
#   AWS_REGION      (기본 ap-northeast-2)
#   ECR_REPO_NAME   (기본 moimyeon/backend)
#   AWS_PROFILE     (기본: 현재 설정)
#
set -euo pipefail

AWS_REGION="${AWS_REGION:-ap-northeast-2}"
ECR_REPO_NAME="${ECR_REPO_NAME:-moimyeon/backend}"

cd "$(git rev-parse --show-toplevel)"

# SHA 태그는 커밋된 소스와 일치해야 하므로 clean tree 강제
if [[ -n "$(git status --porcelain)" ]]; then
  echo "ERROR: 워킹트리가 clean 하지 않다. 먼저 커밋해라." >&2
  git status --short >&2
  exit 1
fi

AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
ECR_REPOSITORY="${ECR_REGISTRY}/${ECR_REPO_NAME}"
GIT_SHA="$(git rev-parse --short=12 HEAD)"
IMAGE_TAG="sha-${GIT_SHA}"
IMAGE="${ECR_REPOSITORY}:${IMAGE_TAG}"

echo "==> Image: ${IMAGE}"

# immutable: 같은 태그가 이미 있으면 스킵
if aws ecr describe-images --repository-name "${ECR_REPO_NAME}" \
     --image-ids imageTag="${IMAGE_TAG}" --region "${AWS_REGION}" >/dev/null 2>&1; then
  echo "==> 태그 ${IMAGE_TAG} 가 이미 ECR 에 있다(immutable). 빌드/푸시 스킵."
  echo "    배포:  ./scripts/deploy.sh ${IMAGE_TAG}"
  exit 0
fi

echo "==> linux/amd64 빌드"
docker buildx build --platform linux/amd64 --provenance=false --pull --load \
  --tag "${IMAGE}" .

echo -n "==> 아키텍처: "
docker image inspect "${IMAGE}" --format '{{.Os}}/{{.Architecture}}'

echo "==> ECR 로그인"
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

echo "==> push"
docker push "${IMAGE}"

echo "==> 완료"
echo "Git SHA: ${GIT_SHA}"
echo "Tag:     ${IMAGE_TAG}"
docker inspect "${IMAGE}" --format 'Digest:  {{index .RepoDigests 0}}'
echo
echo "다음: EC2 에서  sudo bash scripts/deploy.sh ${IMAGE_TAG}"
