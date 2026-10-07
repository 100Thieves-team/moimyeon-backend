# MOI-484 미결정 사항

## 1. Slack 채널과 webhook

- 상태: 해결
- 코드 계약은 `SLACK_API_SPEC_WEBHOOK_URL` repository secret을 사용한다.
- 2026-08-27 사용자가 실제 채널과 secret 준비 완료를 확인했다.

## 2. 알림 브랜치 범위

- 상태: 해결
- API 문서는 dev/main 모두 게시하되 Slack 변경 알림은 dev에서만 보낸다.

## 3. 알림 실패 정책

- 상태: 해결
- Slack 실패가 공개 API 문서 배포를 막지 않도록 알림 스텝을 non-blocking으로 둔다.
- 사용자 피드백에서 변경 요청이 없어 기존 non-blocking 정책을 유지한다.

## 4. 변경 판정 범위

- 상태: 해결
- operation 자체와 그 operation이 참조하는 request/response/parameter/schema의 구조 변경을 알림 대상으로 둔다.
- 동적 값 오탐을 막기 위해 예시를 제외하고, 사용자 결정에 따라 `summary`·`description`도 제외한다.
