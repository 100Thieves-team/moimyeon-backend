Linear 이슈 없음. 사용자 요청에 따른 Terraform dev 적용 생략 수정.

# Terraform 아침 drift 알림 원인 진단

상태: 로컬 조건 수정·회귀 계약·정적 검증·QA 리뷰 완료. AWS 적용·복구 확인은 미실행. 운영 조회는 읽기 전용으로 수행했다.

## 증거와 타임라인 (KST)

- 2026-09-03 22:32: 커밋 `49868485`가 Bedrock 허용 리소스를 Sonnet 5의
  foundation model/inference profile에서 서울 리전 Claude 3.5 Sonnet으로 제한했다.
- 2026-09-03 23:32 시작한 Terraform Apply run 33767410322의 dev plan은 IAM 1건
  변경을 계산했지만 `apply-shared`, `apply-dev`, `sync-dev-variables`가 skipped였다.
- 2026-09-04 00:08:03: AWS 공개 SSM의 ECS AL2023 권장 AMI 파라미터가 version 146으로
  갱신됐다. `ecs.tf`는 이 가변 파라미터를 직접 `image_id`로 사용한다.
- 2026-09-04 아침 run 33816973707에서 IAM 정책과 시작 템플릿 2건 drift를 감지했다.
- 2026-09-05 run 33946394794: shared plan은 No changes, dev plan은
  0 add / 2 change / 0 destroy. plan의 current/apply_required/plan_prefix 출력은 정상 등록됐으나
  apply-dev는 skipped였고 전체 workflow는 success였다.
- 2026-09-07 07:53:48–07:54:59 run 34065331410에서도 같은 2건을 감지했다.
- 2026-09-07 AWS 조회: 실제 dev task inline policy에는 이전 Sonnet 5 권한이 남아 있다.
  ECS 시작 템플릿의 default/latest version은 모두 3이며 현재 AWS 권장 AMI와 다르다.

## 원인

`terraform-apply.yml`의 shared no-op → apply-shared skipped → plan-dev success →
apply-dev skipped 경로다. plan-dev에는 `always()`가 있지만 apply-dev에는 상태 함수가
없어 암묵적 `success()`가 적용되며, 상위 dependency chain의 skip이 전파된다.
코드·실행 로그·GitHub needs/status 함수 문서를 대조했고 읽기 전용 QA 리뷰도 같은 결론이다.
따라서 IAM 변경은 Git에만 반영됐고, 이후 AWS 권장 AMI 갱신도 적용되지 않은 채 누적됐다.
이번 감지 자체는 수동 콘솔 변경의 증거가 아니다.

## 수정 제안과 검증 범위

- apply-dev 조건에 `!cancelled()`를 명시하고 기존 plan 성공/current/apply_required 조건을
  유지한다. select 성공도 명시적으로 확인한다. 로컬 조건 수정은 완료했고 apply는 수행하지 않았다.
- shared no-op/dev 변경 시 적용, shared 실패·취소·stale plan 시 적용 차단,
  dev no-op 시 적용 생략과 변수 sync 경로를 검증한다.
- PR에서 새 plan을 검토한다. IAM 권한 범위 변경과 새 AMI가 이후 생성되는 ECS 호스트에
  미치는 영향을 확인하고, 적용 뒤 drift 0건을 확인해야 복구로 기록할 수 있다.
- 가변 권장 AMI 갱신은 별도 정상 변경 원인이다. 고정 AMI와 명시적 업그레이드 PR 방식은
  자동 최신화와의 운영 선택이며 이번 진단에서 변경하지 않는다.

## 근거

- https://github.com/100Thieves-team/moimyeon-backend/actions/runs/33767410322
- https://github.com/100Thieves-team/moimyeon-backend/actions/runs/33946394794
- https://github.com/100Thieves-team/moimyeon-backend/actions/runs/34065331410
- https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-jobs
- https://github.com/actions/runner/issues/2205
- `.github/workflows/terraform-apply.yml` (plan-dev / apply-dev)
- `infra/terraform/modules/moimyeon-environment/iam.tf` (SummarizeResumeWithBedrock)
- `infra/terraform/modules/moimyeon-environment/ecs.tf` (ecs_optimized_ami / aws_launch_template.ecs)
