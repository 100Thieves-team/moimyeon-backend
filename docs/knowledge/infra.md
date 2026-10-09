# 인프라·배포 — 파이프라인 불변식

> 출처: 팀 배포 개편 확정 설계(MOI-432, 2026-08-25 머지 — PR #104·#105).
> 상세 결정 전체는 [`.worklog/MOI-432/decisions.md`](../../.worklog/MOI-432/decisions.md)
> (DR-001~025)에 있다 — 이 문서는 에이전트가 위반하면 안 되는 불변식만 추린다.
> 판독 기준 보강: 전문 에이전트 블루프린트 §7 DevOps·§8 Infra
> (AWS 문서 원전 인용 포함, 로컬 `_workspace/harness-reference/` 비커밋).
>
> **이 문서는 원본 없이 자립한다** — 판단 기준을 본문에 옮겨 담았다. 로컬
> 경로는 작성 이력이지 참조 의무가 아니다.
>
> 에이전트 중립 문서다 — 현재 소비자는 infra-change·incident-response 스킬.
> 코딩 에이전트가 워크플로·Terraform을 수정할 때 아래 불변식을 위반하는
> 변경을 만들면 안 된다.

## 배포 파이프라인 불변식 (팀 결정)

- **CI가 검증한 트리만 배포** — dev 배포는 dev push로 시작하지만, AWS
  자격증명 전에 머지 커밋과 같은 트리의 PR head가 required check `build`를
  통과했음을 증명한다(`verify-pr-ci.sh`). dev ruleset의 PR 필수·strict(머지 전
  branch update)가 이 전제를 만든다 — ruleset을 약화하면 이 규칙도 무너진다.
  이 검증 없이 push에 반응하는 배포 경로를 만들지 않는다 (MOI-565, DR-001 대체).
- **이미지는 PR CI에서 한 번 빌드** — 내부 PR CI가 `tree-<트리>-run-<실행>-<시도>`
  태그로 candidate ECR에 올리고, 배포는 검증한 `build`와 같은 실행 시도의
  `image` job이 성공했을 때만 그 digest를 복사한다. 트리만으로 후보를 고르면
  다른 PR이 먼저 올린 이미지가 승격될 수 있다. PR 역할은 candidate 저장소 쓰기만
  가진다. 믿을 후보가 없으면 배포가 빌드한다 (MOI-565).
- **Terraform 경계 대기는 배포 lock 밖에서** — apply·sync가 같은
  `deploy-aws-dev` lock을 쓰므로 lock을 잡고 기다리면 서로 막힌다. lock 안에서는
  기다리지 않고 확인만 한다 (MOI-565).
- **Terraform은 바뀐 커밋에서만, 배포는 적용 경계만 기다린다** — 마지막 적용
  SHA 이후 `infra/terraform`이 그대로면 Terraform Apply를 생략하고, 배포는
  적용되지 않은 Terraform 변경이 앞설 때만 기다린다
  (`wait-for-terraform-boundary.sh`). 두 판정의 경로 범위는 같아야 한다
  (MOI-565, DR-013 일부 대체).
- **build once, promote** — live는 재빌드하지 않는다. dev에서 검증된
  이미지 digest를 ECR 태그 승격으로 배포한다. bootJar는 Gradle 캐시가
  이어지는 러너에서 한 번에 만들고(`build-image-jars.sh`), Dockerfile
  multi-target이 그 jar 디렉터리를 빌드 컨텍스트로 API·Worker 이미지를
  조립한다. Docker 안에서 Gradle을 돌리지 않는다 (MOI-588).
- **롤백 = SSM deployment bundle의 exact 복원** — 재빌드 없이 복귀한다.
  성공한 배포마다 `/moimyeon/{env}/deployments/{sha12}` manifest(source
  SHA·API/Worker image digest·exact task definition ARN)가 기록되고,
  `deployed-{env}-{sha12}` ECR marker가 있는 이미지만 승격·롤백 입력으로
  인정된다 (DR-018·019). ECS에는 태그가 아니라 `repository@sha256:digest`만
  전달한다 (DR-016). 롤백 실행은 개발 플랫폼 actor 또는 break-glass
  dispatch — 에이전트가 만들 수 있는 우회 경로가 아니다 (DR-009).
- **배포 컨트롤러는 ECS native** — live Core API는 `BLUE_GREEN`, dev Core API는
  배포 속도를 위해 `ROLLING`, Worker(ALB 없음)는 `ROLLING`. **CodeDeploy 제어면을
  새로 만들지 않는다** (DR-012). 수작업 폴링 bash도 다시 들이지 않는다. 단,
  ECS 내장 안정화 waiter는 10분에 포기해 live blue/green(서버 증설·health grace·
  bake)보다 짧으므로 승격·롤백 스크립트는 기한을 지정한 안정화 대기를 쓴다 (MOI-581).
- **live 배포의 책임자는 main 머지자다** — required reviewer·Environment
  승인 게이트를 두지 않는다(DR-015, 초기 가정을 뒤집은 확정). 대신 계보·
  digest·marker 검증이 전부 fail-closed다: 검증을 약화하는 변경은 승인
  게이트를 없애는 것과 같다. main 머지가 live 승격을 자동 생성한다 (DR-008).
- **배포 순서**: API 안정화 후 Worker 배포. Worker 빌드는 API 안정화
  대기와 병렬 (DR-003).
- **문서만 바뀐 커밋은 배포하지 않는다** — 첫 부모 diff로 판정 (DR-005).
- **배포 성공/실패/롤백은 Slack 알림 스텝을 유지한다** — dev/live webhook
  분리, `always()` 실행이되 알림 실패가 배포 결과를 덮지 않는다 (DR-010).
  webhook 미설정·전송 실패는 배포를 실패시키지 않고 실행 경고와 요약으로
  드러낸다. dev는 Terraform Apply 결과도 알리며, 더 새 커밋에 밀린 실행은
  공통 workflow가 job을 실패로 끝내므로 실패 판정보다 먼저 걸러야 한다 (MOI-490).
- **blocking smoke를 유지한다** — `/actuator/health/readiness` +
  `/v1/terms`, 호출당 5초·최대 3회·전체 60초. live는 전환 전 실패 시 전환
  금지, 전환 후 실패는 자동 롤백 신호다 (DR-011).

## Terraform 운영 불변식 (팀 결정)

- plan은 PR에서 CI가 수행하고 **sanitized 요약(자원 주소·액션)만**
  코멘트로 남긴다. raw plan은 과거 state 값을 포함할 수 있어 KMS 암호화
  private S3에만 둔다 — GitHub artifact로 올리지 않는다 (DR-025).
- apply는 머지 후 CI가 merged SHA의 exact plan을 다시 만들어 **사람 승인
  없이 자동 실행한다** (checksum 동일 plan만). 따라서 **PR의 plan 판독이
  사실상 마지막 사람 게이트다** — 머지가 곧 apply 결정이다 (DR-013).
  **로컬·에이전트 apply는 계속 금지.** live·rollback 경로는
  `MOIMYEON_*_ENABLED` flag 뒤에서 fail-closed다 (DR-017).
- **비민감 환경값의 원본은 Git이다** — `envs/{env}/{env}.tfvars` 세 파일만
  추적하고, 공식 실행은 `terraform-command.sh`의 explicit `-var-file`만
  쓴다. `terraform.tfvars`·`*.auto.tfvars`·임의 `-var`는 공식 경로에서
  거부된다 (DR-023). tfvars 키는 CI allowlist 계약으로 고정돼 있다.
- **시크릿 값은 Terraform이 소유하지 않는다** — Terraform이 SecureString을
  생성하면 값이 state·plan에 남는다. 앱 시크릿은 **사전 생성** SSM ARN만
  참조하고(DR-014·022·024), 신규 RDS admin은 RDS-managed Secrets Manager를
  쓴다. GitHub secret에는 AWS 밖 시크릿(Slack webhook 등)만 남긴다.
- apply 후 Variables sync가 별도 job으로 실행된다 — tf 출력과 GitHub
  variables의 드리프트를 만들지 않는다.
- dev 배포 설정은 Terraform이 SSM `/moimyeon/dev/deploy/config`에 비민감
  값만 게시하고, 배포는 그 값을 검증해 읽는다 (MOI-565). live도
  `/moimyeon/live/deploy/config`를 게시하고, 승격은 task definition 원본 틀을
  여기서 읽는다. GitHub 변수는 workflow 실행이 시작될 때 값으로 고정되므로,
  Terraform이 교체하는 원본 틀은 SSM에서 읽고 변수로 받는 나머지 배포 대상은
  배포 전에 SSM 값과 대조해 다르면 멈춘다 (MOI-512).
- 매일 드리프트 감지 plan이 돌고 변경이 있으면 실패한다 — 콘솔 수동
  변경은 드리프트로 잡힌다는 전제로, 지속 변경은 반드시 IaC로.
- `infra/terraform/tests/*.sh` 계약 검사가 CI에서 위 불변식 일부를
  기계로 고정한다 — 계약 검사를 삭제·약화하는 변경은 불변식 위반이다.

## GitHub Actions 정책 (블루프린트 §7)

- workflow `permissions`는 최소로: 기본 `contents: read` +
  `id-token: write`(OIDC), job별 필요 권한만 추가.
- 외부 Action은 full SHA pin. fork PR에는 secrets·write token 차단.
- 시크릿 스캐너는 PR·push의 공통 변경 범위를 사용하고, 비교 범위가 없는 새 ref는
  HEAD에 도달 가능한 Git 이력을 검사한다. config·ignore 경로는 명시한다.
- 같은 환경 동시 배포를 막는 concurrency group.
- 로그에 secret·OIDC token을 출력하지 않고, Docker build secret을
  `ARG`나 레이어에 남기지 않는다.

## Terraform plan 판독 — 위험 요소 (블루프린트 §8)

plan에 다음이 보이면 사유·복구 계획 없이 진행하지 않는다:

- 리소스 **replacement** (특히 RDS·데이터 저장소의 삭제·재생성)
- public IP 노출, `0.0.0.0/0` 인그레스
- IAM action·resource 확대, cross-account trust
- KMS key 정책 변경·삭제 예약, backup·encryption 비활성화
- live(운영) route/DNS 변경
- 롤백 불가능한 변경, 예상 비용 급증

## 우리가 겪은 것

배포·Terraform 사건 레슨은 MOI-432 작업부터
[`operations.md`](operations.md)의 "우리가 겪은 것"에 쌓이고 있다
(tfvars 재현 불가, SecureString state 잔존, concurrency pending 대체,
live state 부재, Docker layer 파일명 등). 운영 사건은 거기에, **불변식으로
굳은 것**은 이 문서 본문에 반영한다.
