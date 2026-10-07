# Terraform dev 적용 생략 수정

- [x] 원인 진단 및 수정 착수: 사용자 “진행해” 승인 (2026-09-07).
- [x] 변경 검증 및 QA 리뷰 결과 승인: 사용자 승인 (2026-09-07).
- [ ] 인프라 plan 승인: 머지 후 CI 자동 apply 대상 확인.
- [x] PR 초안 승인: 사용자 “승인. 다음단계 진행해” (2026-09-07).

범위: apply-dev 실행 조건과 회귀 계약. shared/live 설정·권한·AMI 선택은 변경하지 않는다.
별도 worktree: `.worktrees/terraform-dev-apply-skip`, branch: `fix/terraform-dev-apply-skip`.
진단 당시 AWS plan: dev 추가 0 / 변경 2 / 삭제 0, shared 변경 0.
미반영 dev 변경: Bedrock IAM 허용 리소스 제한, ECS 권장 AMI 갱신.
직접 apply·머지·운영 변경은 하지 않는다.

## 구현·검증 결과

- apply-dev에 `!cancelled()` 및 select 성공 검사를 추가했다. 기존 plan 성공/current/
  apply_required와 재사용 apply의 mutation lock 뒤 freshness 검증은 유지한다.
- apply-dev 블록 한정 회귀 계약: 기존 workflow에서는 실패, 수정 후 통과.
- Terraform CI/config/bootstrap, deploy/release 계약, apply boundary waiter: 6개 통과.
- Terraform 1.15.9 fmt 및 dev/shared/live backend 없는 validate 통과.
  기존 service discovery `failure_threshold` deprecated 경고만 있다.
- GitHub 공식 `@actions/expressions` 0.3.61로 실제 조건식 13개 시나리오 통과:
  shared no-op/dev 변경, shared 적용 성공/dev 변경, shared 적용 실패, shared plan 실패,
  dev plan 실패·생략, stale, current 누락, changes 누락, dev no-op, CI 비활성,
  select 실패, workflow 취소. shared 실패 시 plan 차단과 dev no-op sync도 확인했다.
  이는 로컬 조건식 검증이며 GitHub 실제 scheduling 검증은 PR/머지 뒤에 남는다.
- 읽기 전용 QA: PASS. 블로킹 지적 없음. 조건식 검사는 CI에서 구조적 회귀를 잡고,
  전체 dependency scheduler는 로컬 검증 범위 밖이라는 한계를 유지한다.
- 애플리케이션/Kotlin 변경 없음. `./gradlew test ktlintCheck` 통과 (191 tasks, 1m 22s).
- PR #122 생성. 최초 head e53b7e31의 CI·Terraform plan·팀 리뷰·CodeRabbit 검사 통과. apply는 하지 않았다.

검증한 커밋: e53b7e31cbb8e73f7d316abf4d81fdf13b30b10b

## PR CI 및 리뷰 반영

- https://github.com/100Thieves-team/moimyeon-backend/pull/122 (자동 연결 MOI-514).
- PR Terraform plan run 34092789778: shared 0건, dev update 2건(IAM task 정책 / ECS 시작 템플릿), 생성·삭제·replacement 없음.
- CodeRabbit의 조건 토큰 존재 검사 한계 지적을 수용했다. 공백을 제거한 전체 conjunction을
  비교해 || 분기로 가드를 우회하는 조건도 거부한다. baseline 통과, OR 우회·취소 가드 삭제·
  freshness 가드 삭제 변형은 모두 계약 실패를 확인했다.
- 리뷰 반영은 계약 검사만 변경한다. workflow 및 AWS 리소스 정의는 최초 검증과 동일하다.
