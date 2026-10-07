# TBD

## T-001: 추가할 trace/debug 로그의 관측 계약

- 상태: 결정됨 (2026-09-21 갱신)
- 결정: 현재 구현된 상태 변경 Service와 HTTP 밖 비동기 경계에 `log.debug` 호출을 추가한다. 읽기 요청은 HTTP 완료 로그와 중복되므로
  제외하며, 그로스 로그는 추가하지 않는다. 메시지는 `도메인.동작 key=value` 형식으로 식별자를 포함한다.
- 영향: formatter가 메시지·MDC를 보존하도록 바뀌어 DEBUG 줄이 동작·식별자 단위로 구분된다. dev에서 DEBUG가 켜진다.
- 근거: 사용자 결정 [decisions.md](decisions.md) D-01.

## T-002: HTTP 밖 흐름의 요청 단위 연결

- 상태: 미결정, 이번 범위 밖
- 내용: scheduler·worker·`@Async` 안에서는 MDC requestId가 없어 outbox·이력서 요약 로그가 HTTP 완료 로그와 묶이지 않는다.
  실행 단위마다 requestId를 발급해 MDC에 넣는 방식이 후보다.
