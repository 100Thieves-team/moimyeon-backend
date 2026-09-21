# MOI-525 컨텍스트

## 이슈 요약

[MOI-525: Trace용 로그 추가](https://linear.app/100-thieves/issue/MOI-525/trace용-로그-추가)는 완료된 [MOI-411: 운영에 필요한 로그 관련 셋팅 추가하기](https://linear.app/100-thieves/issue/MOI-411/운영에-필요한-로그-관련-셋팅-추가하기)의 기반 위에 trace·debug 목적의 로그를 추가하고, 그로스 해킹용 로그는 별도 이슈로 남기는 작업이다. 이슈에는 댓글·첨부 파일·PRD 링크가 없다. Notion 검색 도구는 현재 세션에 제공되지 않는다.

MOI-411의 결과물은 [PR #127](https://github.com/100Thieves-team/moimyeon-backend/pull/127)이며, 이슈 본문은 Kotlin Logging과 S3 저장만 언급한다.

## 요구사항 핵심

- trace·debug 용도 로그를 추가한다.
- 그로스 해킹용 로그는 이 작업 범위에서 제외한다.

[팀 위키 로깅 아키텍처와 사용 가이드](wiki://100thieves/topics/t-moimyeon-로깅-아키텍처와-사용-가이드)는 다음 계약을 제시한다.

- HTTP 요청 단위 추적은 `RequestLogWriter`가 Servlet 완료 시점에 한 번 기록한다. Controller별 시작·종료 로그를 중복 추가하지 않는다.
- TRACE는 제한된 조사에만, DEBUG는 좁은 진단 logger에 사용하며, dev·staging·live의 현재 앱 logger 기본 수준은 INFO다. local만 DEBUG다.
- 일반 `log.debug`·`log.trace`의 메시지와 payload는 보존되지 않고 `application.log`로 정제된다. 유효한 requestId와 traceId·spanId만 MDC에서 보존한다.
- 업무 의미나 검색 가능한 필드가 필요하면 사건 소유 지점·성공 시점·허용 필드를 정한 타입과 writer를 만들고, 정제기가 이를 검증하도록 확장해야 한다.
- TRACE·DEBUG는 CloudWatch debug에 3일 보관하며, 성장 분석은 commit 이후 확정된 업무 사건을 별도 계약으로 기록한다.

사용자 확인으로 이번 작업은 새 구조화 업무 사건을 만드는 것이 아니라, 현재 구현된 흐름에 `log.xxx` 호출을 추가하는 범위다. HTTP 완료 로그와 예외 로그는 이미 있으므로, 상태 변경 Service와 HTTP 밖 비동기 경계를 DEBUG 수준으로 보강한다.

## 관련 코드

- `support/logging/README.md`: MOI-411 로깅 구조와 허용된 이벤트·필드 정책.
- `support/logging/src/main/kotlin/io/plady/moimyeon/support/logging/RequestLogWriter.kt`: HTTP 완료·느린 요청의 구조화 로그 작성.
- `support/logging/src/main/kotlin/io/plady/moimyeon/support/logging/LogSanitizer.kt`: stdout으로 허용할 구조화 필드 정제.
- `core/core-api/src/main/kotlin/io/plady/moimyeon/core/api/logging/HttpRequestLoggingFilter.kt`: 요청 로그 컨텍스트와 보안 조기 종료 경로 분류.
- `core/core-api/src/main/kotlin/io/plady/moimyeon/core/api/logging/HttpRequestLog.kt`: requestId, traceId, spanId와 HTTP 요청 완료 로그 조립.
- `support/logging/src/test/kotlin/io/plady/moimyeon/support/logging/RequestLoggingTest.kt`: 요청 로그 단위 테스트.
- `core/core-api/src/test/kotlin/io/plady/moimyeon/core/api/logging/HttpRequestLoggingTest.kt`: HTTP 요청 로그 단위 테스트.
- `support/logging/src/main/resources/logback/logback-{dev,staging,live}.xml`: 운영 계열 앱 logger의 현재 INFO 기본 수준.

## 작업 경계

- 성장 분석·그로스 해킹 목적의 이벤트는 추가하지 않는다.
- 로그 인프라, S3, FireLens, Terraform 변경은 포함하지 않는다.
- 사건과 필드가 확정되기 전 임의의 도메인 또는 요청 로그를 추가하지 않는다.
