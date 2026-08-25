# MOI-432 구현 계획

## 승인 체크포인트

- [x] dev 배포 파이프라인 슬라이스와 정적 검증 결과 승인
- [x] Terraform plan `변경 없음(0/0/0)` 판독 승인
- [x] provider 6.x·설정 권위·secret ownership·live ECS blue/green Terraform plan 승인
- [x] 커밋·PR 승인

체크박스는 사람이 승인한 뒤에만 표시한다. 2026-08-25 확정 기준에 따라 다음 슬라이스 진행 승인을 받았다.

## 이번 슬라이스의 배포 스펙

1. dev push의 CI가 성공하면 해당 CI의 정확한 head SHA를 배포 후보로 고른다.
2. 변경 파일이 문서와 Markdown뿐이면 AWS 자격 증명을 얻거나 이미지를 만들지 않고 끝낸다.
3. Core API와 Worker bootJar를 한 Gradle 호출에서 만든 뒤 `core-api`, `core-worker` Docker target으로 분리한다.
4. Core API 이미지를 ECR에 올리고 ECS 배포를 시작한 직후 Worker 이미지 빌드를 백그라운드에서 시작한다.
5. API 안정화와 Worker 빌드가 모두 성공한 경우에만 Worker task definition을 갱신·배포한다.

실패 후 보존 결과:

- CI 실패: 배포 워크플로의 AWS job이 실행되지 않는다.
- 문서 전용 변경: 기존 ECS·ECR·SSM 상태를 그대로 둔다.
- API 이미지 빌드 실패: API·Worker 배포를 시작하지 않는다.
- Worker 이미지 빌드 실패: 성공한 API 배포는 유지하고 Worker task definition은 바꾸지 않으며 워크플로는 실패한다.
- API 배포 실패·롤백: Worker 이미지는 만들어질 수 있지만 Worker task definition과 서비스는 바꾸지 않는다.
- Worker 배포 실패·롤백: 이미 안정화된 API 배포는 유지한다.

## 변경 지점과 순서

1. `infra/terraform/tests/deploy-workflow-contract.sh`
   - 위 스펙을 파일 구조와 실행 순서에 대한 정적 계약으로 먼저 고정한다.
2. `Dockerfile`
   - 공통 builder에서 두 bootJar를 한 번에 만들고 runtime target을 분리한다.
3. `.github/workflows/deploy-aws.yml`
   - 독립 push를 `workflow_run(CI 성공, dev)`으로 전환한다.
   - CI head SHA checkout과 문서 전용 변경 gate를 둔다.
   - API ECS 안정화 대기 중 Worker target을 빌드하고 결과에 따라 Worker 배포를 통제한다.
   - 외부 Action을 full SHA로 고정하고 최소 권한·환경별 concurrency를 유지한다.
4. `infra/terraform/scripts/resolve-deploy-candidate.sh`
   - concurrency 획득 뒤 dev 계보에서 가장 최신인 CI 성공 push revision을 선택한다.
5. `.github/workflows/ci.yml`
   - 배포 계약 테스트를 CI에 포함하고 변경하는 Action 참조를 full SHA로 고정한다.
6. `infra/terraform/README.md`
   - CI-gated dev 배포와 임시 live freeze를 운영자에게 명시한다.
7. `.worklog/MOI-432/decisions.md`
   - 구현으로 확인된 경계와 실패 보존 정책을 기록한다.

## 검증

- `bash infra/terraform/tests/deploy-workflow-contract.sh`
- GitHub Actions YAML 구문 정적 검사(actionlint 사용 가능 시)
- `docker build --target core-api`와 `docker build --target core-worker` 또는 동등한 BuildKit 검증
- `./gradlew test ktlintCheck`
- Terraform 파일 변경 여부 확인. 변경이 없으면 plan 결과를 `0 add / 0 change / 0 destroy`로 판독한다.

현재 실행 결과:

- 배포 계약 검사 통과
- actionlint 1.7.12로 변경한 두 workflow 검사 통과
- 배포 후보 resolver: 실제 GitHub CI 이력에서 최신 성공 dev 선택, 더 최신 미검증 descendant가 있어도 직전 성공 dev 유지 확인
- Docker `core-api`, `core-worker` target 실제 빌드와 non-root runtime 파일 검사 통과
- Terraform 파일 변경 없음: `0 add / 0 change / 0 destroy`
- `./gradlew test ktlintCheck` 통과
- code-reviewer 필수·권장 지적 없음
- qa-reviewer PASS, 잔여 위험은 후속 슬라이스와 E2E 검증 범위로 기록

## 영향

- 환경: dev 자동 배포만 변경. Terraform state와 AWS 리소스 자체는 변경하지 않는다.
- live: main push 재빌드 경로를 제거한다. live 승격 경로가 구현되기 전까지 자동 배포하지 않는다.
- 외부 소비자: 백엔드 개발자와 배포 운영자가 CI 완료 후 배포 실행 순서·실패 위치를 다르게 보게 된다.
- API 문서·OpenAPI·프론트 계약: 영향 없음.

## 다음 슬라이스: 승격·롤백·배포 후 검증

자연어 흐름:

1. dev 배포가 성공하면 immutable API·Worker image와 task definition을 deployment bundle로 식별한다.
2. main 머지는 대응하는 dev bundle을 재빌드 없이 live ECR로 복사하고, digest 동일성을 확인한 뒤 live를 갱신한다.
3. 개발 플랫폼은 bundle을 선택해 rollback workflow를 호출한다. 브라우저는 AWS를 직접 호출하지 않는다.
4. API 안정화 뒤 readiness와 `/v1/terms`를 검사하고, 성공한 경계만 SSM `last deployed`로 commit한다.
5. 성공·실패·롤백은 Slack으로 알리되 알림 실패가 배포 결과를 바꾸지 않는다.

실패 후 보존 결과:

- source SHA·dev 배포 기록·ECR marker/ledger digest 불일치: live ECS mutation 전에 중단한다.
- image copy 실패·digest 불일치: live ECS task definition을 등록하지 않는다.
- API rollout·smoke 실패: Worker를 변경하지 않고 API SSM도 이전 값을 유지한다.
- Worker rollout 실패: API·Worker를 모두 직전 live bundle로 보상하고 두 SSM 값을 이전 상태로 유지한다.
- Slack 실패: 배포 결과는 유지하고 workflow warning만 남긴다.

변경 예정:

- `infra/terraform/scripts/`: image copy, task definition 교체·안정화, smoke, Slack 알림 도구
- `.github/workflows/promote-live.yml`: main CI 성공 뒤 자동 digest 승격
- `.github/workflows/rollback-aws.yml`: 개발 플랫폼과 break-glass가 호출하는 rollback 계약
- `.github/workflows/deploy-aws.yml`: dev smoke와 Slack 알림 연결
- `infra/terraform/scripts/sync-github-variables.sh`: smoke URL 등 비밀값이 아닌 output 동기화

선행 제약:

- live Core API native blue/green과 전용 promotion IAM role은 다음 Terraform 슬라이스의 plan 승인 전에는 활성화하지 않는다.
- workflow는 `MOIMYEON_LIVE_DEPLOY_ENABLED=true`가 명시되기 전 fail-closed 상태를 유지한다.
- Slack webhook 값, AWS 운영 리소스, GitHub repository settings는 이 작업에서 직접 생성·변경하지 않는다.

현재 실행 결과:

- live 승격·개발 플랫폼 rollback workflow 작성, actionlint 통과
- release workflow 정적 계약과 전체 shell syntax 검사 통과
- Buildx manifest digest 출력과 tag→digest 정규화 실제 registry 조회 통과
- readiness + `/v1/terms` smoke 로컬 HTTP 검증 통과
- Slack Incoming Webhook JSON payload 로컬 HTTP 검증 통과
- 실제 GitHub dev CI 성공 source 조회 통과
- mutable image URI의 ECS 적용 거부 확인
- AWS·Slack 운영 리소스 변경 없음, live·rollback enable flag 기본 비활성
- 배포 성공 뒤에만 `deployed-{env}-{sha12}` marker를 만들고 승격·rollback은 marker만 허용
- smoke·ECS waiter·revision 검증·SSM commit 실패 시 이전 ECS task definition과 SSM image 복원
- live promotion과 `both` rollback의 Worker 실패 시 API 직전 상태 보상
- rollback actor·입력·source 검증을 무권한 authorize job으로 분리하고 OIDC job tooling SHA 고정
- rollback bundle이 과거 image뿐 아니라 과거 task definition ARN 전체를 복원하도록 계약 강화
- main CI 완료 역순에서도 deploy lock 뒤 최신 성공 main을 재선택하고 dev parent와 runtime diff 검증
- dev ledger와 `deployed-dev-*` marker digest를 API·Worker 모두 교차검증
- docs-only dev parent에 ledger가 없으면 runtime-equivalent first-parent deployment bundle을 선택
- marker·ledger commit 중 하나라도 실패하면 두 live service를 직전 bundle로 보상
- live API의 native ECS `BLUE_GREEN` 구성과 API·Worker `desiredCount > 0`을 확인하지 못하면 promotion 시작 전 중단
- ECR lifecycle은 untagged 7일 정리만 허용하고 deploy role에는 marker 삭제 권한을 주지 않음
- 최종 code-reviewer PASS, qa-reviewer PASS(필수·권장 지적 없음)
- actionlint 1.7.12, workflow YAML parse, shell syntax, release/deploy 계약, `terraform fmt -check`, `git diff --check` 통과

## Terraform plan 판독: provider 6.x·ledger·native blue/green

검증 도구:

- Terraform 1.15.9 darwin arm64 binary의 HashiCorp SHA-256 checksum 확인
- AWS provider `6.61.0`, random provider `3.9.0` lock 갱신
- dev/live/shared `terraform validate` 통과

### dev 실제 state plan

- 최종 저장 plan: `/tmp/moi432-dev-config-v4.tfplan`.
- 요약: `2 add / 4 change / 2 destroy`로 표시된다.
- replacement 2건은 `aws_ecr_lifecycle_policy.app`, `notification_worker`의 **정책 객체 교체**다.
  ECR repository와 image를 삭제하는 replacement가 아니다.
- ECR Core API repository: `MUTABLE → IMMUTABLE` in-place.
- GitHub deploy role trust·ledger SSM 권한: in-place. ECR marker 삭제 권한은 추가하지 않는다.
- Launch template: 최신 ECS optimized AMI refresh에 따른 in-place 새 version. 기존 인스턴스를 즉시 교체하지 않는다.
- OAuth·JWT SSM과 JWT random resource: `removed { destroy = false }`로 state 추적만 제거하며 AWS parameter는 삭제하지 않는다.
- RDS, ECS service, Cloud Map, VPC, ALB replacement 없음.
- 의도적으로 유지한 경고: Cloud Map `failure_threshold=1`. 제거하면 service replacement가 발생하므로 provider가
  no-replacement migration을 제공할 때까지 유지한다.
- 운영 전 수동 조치: OAuth/JWT credential rotation 영향 검토, versioned state 과거본 접근·보존 점검.

### live plan

- 최종 저장 plan: `/tmp/moi432-live-config-v4.tfplan`.
- 원격 state가 존재하지 않아 실제 plan은 **전체 신규 bootstrap**이다.
- committed `live.tfvars` 기준: `96 add / 0 change / 0 destroy`.
- native blue/green 핵심 생성: alternate target group, production listener rule, ECS load-balancer infrastructure role,
  `aws_ecs_service.app`의 `BLUE_GREEN` strategy.
- example tfvars는 API·Worker·EC2 capacity가 모두 0이다. 실제 capacity와 desired count를 1 이상으로 올리고
  smoke를 확인하기 전 `MOIMYEON_LIVE_DEPLOY_ENABLED`를 켜면 안 되며 workflow도 이를 거부한다.
- RDS·VPC·ALB·IAM 등 live 환경 전체를 새로 만드는 plan이므로 이 이슈의 blue/green 변경만 분리해 apply할 수 없다.
- live API capacity는 0이며 OAuth client ID도 의도적으로 null이다. client ID PR, pre-created JWT/OAuth/vendor SecureString,
  외부 DNS/ACM 검증, 실제 capacity 검토 없이는 activation 금지.
- live RDS admin password는 RDS-managed Secrets Manager를 사용하며 Terraform plan/state에 password 값을 만들지 않는다.
  ECS는 pre-created SSM application DB password만 읽고 admin secret 권한을 받지 않는다.

### shared 실제 state plan

- 최종 저장 plan: `/tmp/moi432-shared-bootstrap-v4.tfplan`.
- `28 add / 0 change / 0 destroy`.
- 신규: rotating KMS key/alias, private plan S3 bucket과 BPA·versioning·KMS encryption·24시간 lifecycle·bucket policy,
  review/drift/apply-plan writer role 3개, shared/dev/live apply role 3개와 각 policy/managed-policy attachment.
- 기존 GitHub OIDC provider와 Route53 리소스는 변경하지 않는다.
- writer는 서로 다른 GitHub Environment subject와 S3 prefix를 사용한다. bucket policy가 KMS header,
  `s3:if-none-match` create-only Put, cross-prefix write·Delete를 fail-closed로 강제한다.
- Terraform Environment는 reviewer·wait timer·branch policy 없는 변수 namespace다. privileged role은 immutable
  repository/owner ID, `refs/heads/dev`, workflow 이름과 reusable `job_workflow_ref`를 추가 검증한다.
- 이 plan은 순환 의존성을 끊기 위한 최초 1회 사람 apply 대상이다. 에이전트는 apply하지 않았다.
- 적용 순서: current dev state의 JWT/OAuth/random forget plan을 사람이 먼저 apply한 뒤에만 shared bootstrap을 apply한다.
  role ARN variable을 숨겨도 ARN 자체가 예측 가능하므로 shared-first 순서는 허용하지 않는다.

### 위험 판독

- 데이터 저장소 삭제·재생성 없음.
- public ingress `0.0.0.0/0` 신규 확대 없음. live 전체 생성 plan의 Worker HTTPS/SMTP egress는 기존 모듈 정책이다.
- IAM 확대: dev/live deploy role에 환경 OIDC subject, bundle SSM read/write, live role의 dev ECR·ledger read를 추가한다.
- shared IAM 신규: plan 역할은 저장소 소유 metadata-only managed policy와 exact state/image marker read만 받고
  application data-plane/secret/KMS decrypt를 거부한다. apply 역할은 PowerUserAccess에 cross-state/artifact/KMS 보호
  Deny와 프로젝트 prefix IAM 관리만 추가한다. protected branch·CI·exact plan·workflow/ref OIDC claim이 권한 경계다.
- apply·GitHub enable flag 설정·시크릿 생성은 실행하지 않았다.

## 설정 권위·Terraform CI 검증 결과

- 공식 비민감 source: committed `shared.tfvars`, `dev.tfvars`, `live.tfvars`.
- GitHub OIDC provider ARN 수동 복사 제거, AWS data source 조회로 전환.
- `terraform-command.sh`: auto-loaded local tfvars와 backend source drift를 거부하고 explicit `-var-file` plan만 생성.
- `TF_VAR_*`, `TF_CLI_ARGS*`, `TF_WORKSPACE`, 외부 `TF_DATA_DIR`를 거부하고 backend init 뒤 default workspace를 재검증.
- pinned Gitleaks current-tree 31MB와 557-commit history scan 통과. allowlist는 정확한 CI/test dummy 값 네 패턴과
  MOI-474 scanner fixture의 기존 fingerprint 세 개에만 한정.
- fork-safe PR plan, daily drift, merged-SHA exact-plan apply workflow와 정적 계약 추가.
- PR review-plan과 merged apply-plan을 서로 다른 GitHub Environment/OIDC role/S3 prefix로 분리하고 create-only upload 사용.
- `shared-foundation`에 KMS·private S3 plan store·분리된 OIDC role을 코드화하고 실제 shared plan `28/0/0` 판독.
- `sync-terraform-bootstrap.sh`로 보호 규칙 없는 6개 Terraform Environment namespace와 role/bucket/KMS Variables를
  dry-run→명시적 `--apply` 순서로 reconcile. enable flag는 항상 false로 남기고 Variables-write token 값은
  ref/workflow-bound apply role만 읽는 사전 생성 SSM SecureString에 둔다.
- dev branch는 shared plan/apply가 성공한 뒤 dev plan을 새로 만들어 old shared state 캡처를 차단.
- apply lock 뒤 branch tip freshness를 재검증하고 stale push는 AWS apply credential 전에 종료.
- 모든 CI-success trigger를 run-name/source에 고정하고 no-op plan까지 수행해 Terraform 변경 뒤 app/docs commit의 역순 CI 완료에도 변경을 누락하지 않음.
- dev deploy/live promotion이 같은 CI SHA의 Terraform Apply run 성공을 기다려 infra+app commit의 실행 순서를 명시.
- plan·deploy·promotion·rollback·Terraform apply concurrency를 `queue: max`로 바꿔 late old run의 latest pending 교체를 차단.
- candidate 이후 docs-only successor는 runtime-equivalent로 허용하고 runtime change만 newer Terraform boundary로 넘김.
- dev/live/shared provider 6.61 isolated validate 통과.
