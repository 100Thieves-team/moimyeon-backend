# MOI-484 컨텍스트

## 이슈 요약

[MOI-484 API Spec 변경 alert 추가](https://linear.app/100-thieves/issue/MOI-484/api-spec-%EB%B3%80%EA%B2%BD-alert-%EC%B6%94%EA%B0%80)는
API 스펙이 변경되면 변경된 API 목록을 프론트엔드 개발자와 백엔드 팀원이 보는 Slack 채널에 보내고,
전송 시점을 PR 머지로 맞추는 인프라 이슈다. Linear 댓글·첨부·연관 이슈는 없다.

팀 Notion의 [2026년 7월 23일 데일리 스크럼](https://app.notion.com/p/3a61bb8f1fbe8024b7bef8986390c322)에는
프론트엔드 협업 필요 사항으로 OpenAPI 스펙 제공 요구가 기록돼 있다. 별도의 MOI-484 PRD나 상세 정책 문서는 찾지 못했다.

## 요구사항 핵심

- PR 머지 뒤 실제 OpenAPI 계약이 바뀐 경우에만 알린다.
- 메시지에는 변경된 HTTP method와 path 목록을 담는다.
- 프론트엔드·백엔드 팀원이 함께 보는 Slack 채널로 보낸다.
- 시크릿 값은 코드·로그·worklog에 남기지 않는다.

## 관련 코드

- `.github/workflows/api-docs-pages.yml`: dev/main push에서 OpenAPI를 생성해 gh-pages의 브랜치별 경로에 배포한다.
- `core/core-api/build.gradle.kts`: REST Docs 결과로 `openapi3.yaml`을 생성·보정·검증한다.
- `infra/terraform/scripts/notify-deployment.sh`: `jq`와 Incoming Webhook을 사용하는 기존 Slack 전송 패턴이다.
- `.github/workflows/deploy-aws.yml`, `promote-live.yml`, `rollback-aws.yml`: Slack 시크릿 주입과 알림 실패 격리의 선례다.
- `.github/workflows/ci.yml`: Actions·shell 정적 계약 검사를 실행하는 CI 진입점이다.

## 작업 경계

- OpenAPI 생성 규칙, Controller·DTO·Service, 공개 API 자체는 변경하지 않는다.
- Slack App·채널·Incoming Webhook과 GitHub secret 값은 생성하거나 변경하지 않는다.
- Terraform·AWS 리소스와 배포 파이프라인은 변경하거나 apply하지 않는다.
- PR 생성·머지는 별도 승인 전에는 수행하지 않는다.
