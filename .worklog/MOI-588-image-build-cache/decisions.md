# MOI-588 결정 기록

## D-1: bootJar는 러너에서 만들고 Docker는 jar만 받는다

- 결정(사람, 2026-10-10): CI `image` job이 러너에서 `setup-gradle` 캐시로 bootJar를 만들고,
  Dockerfile은 그 jar 디렉터리를 빌드 컨텍스트로 받아 레이어 추출·AOT 학습만 한다.
  배포의 대체 빌드도 같은 스크립트를 쓴다.
- 이유: Docker 안 Gradle 캐시는 BuildKit cache mount라 GHA 러너 사이에 이어지지 않는다.
  러너의 Gradle 캐시는 `build` job에서 이미 이어지고 있다. 컨텍스트가 jar뿐이라 문서·하네스
  변경이 이미지 레이어 캐시를 깨지 않고, 이미지별로 필요한 jar만 받는다.
- 검토한 다른 안: Docker 안 빌드 + 캐시 저장·복원 단계 추가(단계가 늘고 레이어 캐시 내보내기
  비용이 남음), 검증 job의 jar 재사용(image job이 검증 뒤로 밀림).
- 비용: 저장소 루트를 컨텍스트로 한 `docker build .`은 더 이상 동작하지 않는다. 쓰는 곳 없음
  (docker-compose는 앱 이미지를 빌드하지 않는다). 루트 `.dockerignore`는 쓰이지 않아 지웠다.
- 대체: `docs/knowledge/infra.md` "build once, promote"의 "Dockerfile multi-target으로 한 빌드에서" 문구.

## D-2: Docker 캐시는 이미지별 scope, mode=min

- 결정: `deployment-images-core-api`·`deployment-images-core-worker`로 scope를 나누고 `mode=min`.
- 이유: 중간 단계는 jar 레이어 추출뿐이라 다시 해도 몇 초다. 비싼 AOT 학습은 최종 단계에 있어
  min으로도 캐시된다. 같은 scope를 두 이미지가 함께 쓰면 마지막에 쓴 쪽이 캐시를 덮는다.
- 결과: 기존 `deployment-images` scope는 더 쓰지 않는다. GHA 캐시는 7일 미사용 시 지워진다.

## D-3: Gradle은 AWS 자격증명·OIDC 토큰 요청·ECR 로그인 없이 실행한다

- 배경(qa-reviewer 필수 지적): 전에는 Gradle이 BuildKit 컨테이너 안에서 돌아 러너 환경을 보지 못했다.
  러너로 옮기면 빌드 스크립트·플러그인 같은 외부 의존성 코드가 같은 job의 AWS 자격증명, OIDC 토큰 요청
  변수, ECR 로그인 정보에 닿는다. 같은 저장소 PR 작성자는 원래 워크플로를 고칠 수 있으므로 새로 늘어나는
  위험은 공급망(의존성 침해) 쪽이다.
- 결정(사람, 2026-10-10): 최소 완화. CI는 jar 빌드를 `Configure AWS credentials` 앞에 둔다.
  `build-image-jars.sh`는 AWS 키 3종과 `ACTIONS_ID_TOKEN_REQUEST_*`를 뺀 환경, 빈 `DOCKER_CONFIG`로
  Gradle을 실행한다(배포 대체 빌드도 같은 스크립트).
- 남는 위험: 같은 job 안이라 러너 파일시스템·프로세스 수준 격리는 아니다. 완전 분리(자격증명 없는 별도 job +
  artifact)는 job 하나와 jar 약 275MB 전송이 늘어 택하지 않았다.

## D-4: Worker 이미지 존재 확인은 한 번만

- qa-reviewer 권고: 대체 빌드 판단과 `Deploy ECS service`가 Worker 이미지를 따로 조회하면 두 번째 조회의
  일시 오류로 jar 없이 docker build가 돌 수 있다. `Deploy ECS service`는 판단 결과를 그대로 쓴다.
- 같은 리뷰의 권고로 `Dockerfile.dockerignore`(jar 두 개만 허용)와 계약 테스트의 순서·허용 목록 검사를 더했다.
