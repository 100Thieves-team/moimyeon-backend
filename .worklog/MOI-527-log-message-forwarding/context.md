# MOI-527 운영 로그 수집 — 메시지·예외 메시지 전달

Linear: MOI-527. 관련: MOI-411(수집기 도입), MOI-525(앱 출력 계약, PR #131).
제품 명세와 무관한 인프라 작업이라 Wiki는 조회하지 않았다.

## 요약

앱(`LogSanitizer`)은 MOI-525부터 message(마스킹·4096자 상한)와 `SafeLogMessage`
예외의 메시지를 stdout JSON에 남긴다. 수집기(`router/v1/sanitize.lua`)는 MOI-411
허용 목록 그대로라 둘 다 버린다. 그래서 dev·live CloudWatch와 S3에는 eventCode와
식별자, 예외 타입과 코드 위치만 남고, 오류가 왜 났는지 알 수 없다. live는
Sentry도 없고, 켜더라도 `SentryPrivacyFilter`가 메시지를 지운다.

## 관련 코드

- `infra/terraform/modules/application-logging/router/v1/` 배포된 수집기 설정. 수정 금지
- `infra/terraform/modules/application-logging/main.tf` `revisions`, `active_revision`
- `infra/terraform/modules/application-logging/README.md` "배포와 롤백"에 v2 추가 절차
- `infra/terraform/modules/application-logging/tests/logging.tftest.hcl` 모듈 plan 테스트
- `infra/terraform/tests/logging_smoke.py`, `logging_send_records.py` 실제 Fluent Bit smoke
- `support/logging/.../LogSanitizer.kt` 앱 출력 계약(RESERVED_FIELDS, 길이 상한)

## 경계

- 앱 출력 계약은 바꾸지 않는다(MOI-525에서 완료).
- MDC·key-value 임의 필드, thread는 이번에 전달하지 않는다(이슈 범위 밖).
- Sentry 전송 정보, 로그 보존 기간·비용 정책은 바꾸지 않는다.
- terraform apply는 하지 않는다. 머지가 적용 승인이다.
