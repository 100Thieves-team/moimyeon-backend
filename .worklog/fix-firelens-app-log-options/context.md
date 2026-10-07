# fix-firelens-app-log-options — 컨텍스트

**Linear 이슈 없음.** dev 배포 실패 진단(2026-09-23)에서 도출.

## 사실

- Deploy AWS 는 2026-09-21 11:17(c31e2137) 이후 4건 연속 실패. 마지막 성공은 05747f1e(09-21 08:50). dev 는
  task-definition core-api:99(이미지 dev-0a257c27)에 멈춰 있다.
- 실패 양상: 새 태스크가 PENDING 으로 25분 → "Timed out waiting for ECS service" → 서킷 브레이커 롤백.
  `describe-tasks`: `log-router` 컨테이너 STOPPED(exit 255), `core-api` PENDING(pullStartedAt null).
- 라우터 로그(`/ecs/moimyeon-dev/core-api/router`):
  `[config] null: unknown configuration property 'max-buffer-size'` → `output initialization failed`.
- 원인 코드: `infra/terraform/modules/application-logging/outputs.tf` `app_log_configurations` —
  awsfirelens 옵션에 `mode`·`max-buffer-size`(Docker awslogs 드라이버 옵션)가 들어가 Fluent Bit `[OUTPUT] null` 로 전달됨.
  도입 커밋 `0eb1f6dd feat(infra): FireLens 로그 수집과 S3 보존 경로 추가`.
- 영향 환경: dev 만(`application_logging_mode = "enabled"` 는 dev.tfvars). live tfvars 는 확인 필요하나 live 배포 경로는 별도 flag 뒤.

## 변경 범위

- `app_log_configurations` 옵션에서 `mode`·`max-buffer-size` 제거(`Name`·`log-driver-buffer-limit` 유지).
- 사이드카 자체·fallback 의 `awslogs` 설정은 awslogs 드라이버가 지원하는 옵션이라 유지.
- `infra/terraform/tests/test_deploy_inputs.py` 픽스처를 실제 옵션과 맞춤. `docs/knowledge/operations.md` 사건 기록.
