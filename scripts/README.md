# 배포 스크립트

core-api 를 ECR 로 빌드·푸시하고 EC2 에 배포하는 스크립트.

## build-and-push.sh — 개발 PC / CI 에서

현재 커밋을 `linux/amd64` 이미지로 빌드해 ECR 에 push 한다.

```bash
./scripts/build-and-push.sh
```

- 태그 = `sha-<git-commit-12>` (immutable, 이미 있으면 스킵)
- **clean 워킹트리 필요** (태그가 커밋과 일치해야 함)
- 사전: `docker buildx`, `aws` CLI (ECR push 권한)
- 환경변수(선택): `AWS_REGION`(기본 ap-northeast-2), `ECR_REPO_NAME`(기본 moimyeon/backend), `AWS_PROFILE`

## deploy.sh — EC2(Session Manager) 에서

ECR 이미지를 pull 해 컨테이너로 띄운다(기존 컨테이너 교체 + health 확인).

```bash
sudo bash scripts/deploy.sh sha-517f3dad0940
```

- 사전: `docker`, `aws` CLI, EC2 IAM Role 에 **ECR Pull 권한**
- 시크릿은 `ENV_FILE`(기본 `/opt/moimyeon/app.env`, `chmod 600`)에서 읽는다 — git 에 올리지 않는다
- 환경변수(선택): `CONTAINER_NAME`(기본 moimyeon-backend), `HOST_PORT`(기본 8080), `ENV_FILE`

## EC2 최초 셋업 (Session Manager 세션)

```bash
# 1) docker 설치/기동 (Amazon Linux 2023)
sudo dnf install -y docker || sudo yum install -y docker
sudo systemctl enable --now docker

# 2) 시크릿 env 파일 (실제 값으로. RDS 는 실제 엔드포인트:3306)
sudo mkdir -p /opt/moimyeon
sudo tee /opt/moimyeon/app.env >/dev/null <<'EOF'
SPRING_PROFILES_ACTIVE=dev
STORAGE_DATABASE_CORE_DB_URL=<rds-endpoint>:3306/moimyeondev
STORAGE_DATABASE_CORE_DB_USERNAME=__CHANGE_ME__
STORAGE_DATABASE_CORE_DB_PASSWORD=__CHANGE_ME__
GOOGLE_OAUTH_CLIENT_ID=__CHANGE_ME__
GOOGLE_OAUTH_CLIENT_SECRET=__CHANGE_ME__
JWT_SECRET=__CHANGE_ME__
EOF
sudo chmod 600 /opt/moimyeon/app.env

# 3) 스크립트 확보 (repo clone 또는 deploy.sh 만 복사) 후 배포
sudo bash scripts/deploy.sh sha-517f3dad0940

# 4) 확인
sudo docker ps
curl -s localhost:8080/actuator/health   # {"status":"UP"}
```

> 배포 태그는 `build-and-push.sh` 가 출력한 `sha-...` 를 쓴다. 앱 코드가 안 바뀌었으면 재빌드 없이 기존 태그로 바로 배포하면 된다.
> `.env.example`(repo 루트)이 필요한 키의 레퍼런스다. 실제 값은 EC2 의 `/opt/moimyeon/app.env` 또는 SSM Parameter Store/Secrets Manager 로 관리 권장.
