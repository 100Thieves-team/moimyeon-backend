# 인프라·배포 — 파이프라인 불변식

> 출처: 팀 배포 개편 결정(2026-08-24 승인 — 배포 시간 단축·CI 게이트·
> build once promote 이슈. 상세 설계는 하위 이슈에서 확정되며 확정 시 이
> 문서를 동기화한다) + 전문 에이전트 블루프린트 §7 DevOps·§8 Infra
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
- **롤백 = 이전 `{env}-{sha12}` 태그의 task definition 재등록** — 재빌드
  없이 복귀한다. Core API는 CodeDeploy blue/green + CloudWatch 알람 자동
  롤백, Worker(ALB 없음)는 롤링 유지. 수작업 폴링 bash를 다시 들이지
  않는다.
- **live는 GitHub Environments 승인 게이트 뒤에서만** — required
  reviewers 없는 live 배포·apply 경로를 만들지 않는다.
- **배포 순서**: API 안정화 후 Worker 배포(현행 유지). Worker 빌드는 API
  안정화 대기와 병렬.
- **문서만 바뀐 커밋은 배포하지 않는다** (paths-ignore).
- **배포 성공/실패/롤백은 알림 스텝을 유지한다** — 알림 없는 배포 경로
  금지.
- **배포 직후 스모크 테스트 스텝을 유지한다** (대상 엔드포인트 [TBD]).

## Terraform 운영 불변식 (팀 결정)

- plan은 PR에서 CI가 수행해 코멘트로 남긴다(읽기 전용 롤). apply는
  머지 + Environment 승인 게이트 뒤에서 CI가 수행한다.
  **로컬 apply는 하지 않는다.** (이행 완료 전 과도기: 로컬 plan 출력을
  PR에 첨부하되 apply는 여전히 사람·CI의 몫)
- apply 후 `sync-github-variables.sh`가 실행된다 — tf 출력과 GitHub
  variables의 드리프트를 만들지 않는다.
- 스케줄 드리프트 감지 plan이 존재한다 — 콘솔 수동 변경은 드리프트로
  잡힌다는 전제로, 지속 변경은 반드시 IaC로.
- **시크릿 원본은 SSM SecureString 단일** — 워크플로는 OIDC 롤로 SSM을
  읽고, GitHub secret에는 AWS 밖 시크릿만 남긴다. tfvars에 시크릿 평문을
  두지 않는다(CI 주입).

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

아직 없다. 사고·리뷰에서 배운 것이 생기면 날짜와 함께 한 줄 추가한다
(형식: README의 "어떻게 늘리는가").
