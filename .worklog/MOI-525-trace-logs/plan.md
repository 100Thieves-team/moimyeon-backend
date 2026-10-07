# MOI-525 구현 계획

- [x] 체크포인트 A: 계획 승인 (2026-09-21)
- [x] 체크포인트 B: 비즈니스 로그 호출 테스트 생략 승인 (2026-09-21)
- [x] 체크포인트 B-2: 출력 allowlist를 일반 로그 계약으로 전환 승인 (2026-09-21, [decisions.md](decisions.md) D-01)
- [x] 체크포인트 B-3: Implement 레이어 전체 공개 메서드에 진입 로그 추가 승인 (2026-09-21, D-04)
- [ ] 체크포인트 C: 구현 승인

## 접근 방식

기존 HTTP 완료 로그와 예외 어드바이스 로그를 중복하지 않는다. `core-api` 도메인 Service의 상태 변경 흐름과 HTTP 밖 비동기 경계에
kotlin-logging `DEBUG` 호출을 둔다. 메시지는 `도메인.동작 key=value` 형식으로 식별자(UUID·숫자 ID)·enum·개수를 넣고, DTO·자유 입력·토큰은
넣지 않는다. HTTP 요청 중에는 기존 MDC의 requestId·traceId·spanId가 같은 줄에 붙어 요청 완료 로그와 묶인다.

이 로그가 실제로 보이려면 `support:logging`의 출력 계약을 바꿔야 했다. 기존 formatter는 등록되지 않은 모든 메시지를
`application.log`로 뭉개서 `room.create`와 `room.cancel`이 구분되지 않았다. 메시지·key-value·MDC·예외 메시지를 보존하는 일반
계약으로 전환하고, `Bearer`·JWT 형태만 방어적으로 마스킹한다. dev의 앱 logger를 DEBUG로 올린다.

## 변경 지점

- `support/logging/.../LogSanitizer.kt`: 메시지(포맷 인자 포함)·key-value·MDC 전체·예외 메시지 보존. 길이 상한과 예외 깊이 상한 유지.
  `eventCode`·`schemaVersion`·`exceptions` 구조는 Fluent Bit 라우터 호환을 위해 유지.
- `support/logging/.../LogMasker.kt`(신규): `Bearer` 헤더값·JWT 형태 마스킹.
- `support/logging/.../SafeLogFormatter.kt`: 텍스트 형식에 메시지·컨텍스트·예외를 사람이 읽는 형태로 출력.
- `support/logging/src/main/resources/logback/logback-dev.xml`: `io.plady.moimyeon` DEBUG. dev-perf·staging·live는 INFO 유지.
- `core/core-api/.../domain/**/*Service.kt`: 상태 변경 공개 메서드 진입에 `log.debug { "도메인.동작 key=value" }`.
- `core/core-api/.../domain/**` `@Component` 79개(Finder·Reader·Manager·Validator·Registrar·Editor·Recorder·Client 등): 공개 메서드 173개 진입에
  `log.debug { "개념.역할.메서드 key=value" }`. 파라미터 중 UUID·Long·Int·Boolean·enum·컬렉션 크기만 넣고 String·DTO·자유 입력·`providerId`는 제외.
  Command 객체를 받는 Manager는 `command.roomId` 등 식별자 필드를 꺼내 넣었다. `JsoupOpenGraphClient`의 SLF4J 호출도 kotlin-logging으로 통일.
- `core/core-api/.../notification/outbox/`: outbox 기록·발행·배치 relay 경계를 eventId와 함께 DEBUG. 기존 WARN·ERROR는 eventId를 유지.
- `core/core-worker/.../`: 메시지 소비·룸 자동 종료 경계 DEBUG, 자동 종료 결과 `completed/total` INFO 유지.
- `ResumeService`·`NotificationRelay`·`RoomAutoCompleteJob`: 기존 SLF4J 호출을 kotlin-logging으로 통일.

## 테스트 목록

- `support:logging`: `SafeLogFormatterTest`(메시지·인자·MDC·key-value 보존, 예약 필드 보호, 토큰 마스킹, 크기 상한, 텍스트 형식),
  `LoggingConfigurationTest`(dev DEBUG, 실제 stdout에 메시지·MDC·예외 메시지 보존), `LoggingBootstrapTest`.
- 비즈니스 코드에 로그 호출이 존재하는지만 검증하는 테스트는 만들지 않는다. 기존 Service·worker·outbox 테스트로 회귀를 확인한다.

## API·외부 소비자 영향

- REST API·DTO·RestDocs·OpenAPI 변경 없음.
- stdout JSON에 `message`·`thread`·MDC 필드가 추가된다. 인프라 `router/v1/sanitize.lua`는 아직 `message`를 버리므로 별도
  infra-change PR에서 같은 방향으로 완화해야 CloudWatch·S3에 도달한다. Sentry 필터는 변경 없음.

## 후속

- infra-change: `sanitize.lua` `strings`에 `message` 추가, 예외 `message` 허용, smoke 테스트 갱신.
- 팀 위키 로깅 가이드 갱신 (MCP 연결 실패로 이번 세션에서는 수정하지 못함).
- HTTP 밖 흐름(scheduler·worker·@Async)의 실행 단위 requestId 발급은 별도 이슈.
