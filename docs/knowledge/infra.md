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

- **CI 성공 없이 배포 없음** — deploy는 CI에 종속(workflow_run 또는
  needs). push에 독립 발화하는 배포 워크플로를 만들지 않는다.
- **build once, promote** — live는 재빌드하지 않는다. dev에서 검증된
  이미지 digest를 ECR 태그 승격으로 배포한다. 이미지 빌드는 Dockerfile
  multi-target으로 API·Worker를 한 빌드에서 뽑는다.
- **롤백 = SSM deployment bundle의 exact 복원** — 재빌드 없이 복귀한다.
  성공한 배포마다 `/moimyeon/{env}/deployments/{sha12}` manifest(source
  SHA·API/Worker image digest·exact task definition ARN)가 기록되고,
  `deployed-{env}-{sha12}` ECR marker가 있는 이미지만 승격·롤백 입력으로
  인정된다 (DR-018·019). ECS에는 태그가 아니라 `repository@sha256:digest`만
  전달한다 (DR-016). 롤백 실행은 개발 플랫폼 actor 또는 break-glass
  dispatch — 에이전트가 만들 수 있는 우회 경로가 아니다 (DR-009).
- **배포 컨트롤러는 ECS native** — Core API는 `BLUE_GREEN`, Worker(ALB
  없음)는 `ROLLING`. **CodeDeploy 제어면을 새로 만들지 않는다** (DR-012).
  수작업 폴링 bash도 다시 들이지 않는다.
- **live 배포의 책임자는 main 머지자다** — required reviewer·Environment
  승인 게이트를 두지 않는다(DR-015, 초기 가정을 뒤집은 확정). 대신 계보·
  digest·marker 검증이 전부 fail-closed다: 검증을 약화하는 변경은 승인
  게이트를 없애는 것과 같다. main 머지가 live 승격을 자동 생성한다 (DR-008).
- **배포 순서**: API 안정화 후 Worker 배포. Worker 빌드는 API 안정화
  대기와 병렬 (DR-003).
- **문서만 바뀐 커밋은 배포하지 않는다** — 첫 부모 diff로 판정 (DR-005).
- **배포 성공/실패/롤백은 Slack 알림 스텝을 유지한다** — dev/live webhook
  분리, `always()` 실행이되 알림 실패가 배포 결과를 덮지 않는다 (DR-010).
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
- 매일 드리프트 감지 plan이 돌고 변경이 있으면 실패한다 — 콘솔 수동
  변경은 드리프트로 잡힌다는 전제로, 지속 변경은 반드시 IaC로.
- `infra/terraform/tests/*.sh` 계약 검사가 CI에서 위 불변식 일부를
  기계로 고정한다 — 계약 검사를 삭제·약화하는 변경은 불변식 위반이다.

## GitHub Actions 정책 (블루프린트 §7)

- workflow `permissions`는 최소로: 기본 `contents: read` +
  `id-token: write`(OIDC), job별 필요 권한만 추가.
- 외부 Action은 full SHA pin. fork PR에는 secrets·write token 차단.
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

- 2026-08-27: PR #106에서 변경 범위 scanner는 통과했지만 build job의 전체 Git 이력 Gitleaks가
  재현되지 않는 누출 1건으로 실패했다. 원인: 같은 CI 안에서 Gitleaks만 공통 `GATE_RANGE`를 쓰지 않고
  전체 ref 이력을 다시 검사했으며 config·ignore 경로도 container 기본값에 의존했다. 재발 방지:
  PR·push 변경 범위를 공유하고, 범위가 없는 새 ref는 HEAD에 도달 가능한 이력을 검사하며 두 경로를 명시한다.
