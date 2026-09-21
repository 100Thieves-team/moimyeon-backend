# Kotlin 코드 스타일

[← 허브로](README.md)

ktlint(`INTELLIJ_IDEA` code style)를 강제한다. 설정은 [.editorconfig](../../.editorconfig) —
trailing comma 허용, star import 금지, function-expression-body 룰 비활성.
아래는 린트가 못 잡는 이 레포의 규칙이다.

## 파일 구성

- **도메인 객체는 파일당 1클래스.** 한 코틀린 파일에 도메인 클래스 두 개를 넣지 않는다
  (`Order.kt` 와 `OrderLine.kt` 는 별개 파일). ktlint 규칙상 파일명은 클래스명과 일치.
- **DTO 는 예외**: 응답 구조상 강결합된 하위 DTO 는 같은 파일에 둘 수 있다 ([api-design.md](api-design.md)).
- Mapper 처럼 상태 없는 변환기는 `object` 로 선언한다(빈으로 만들지 않는다).

## 클래스·값

- 도메인 모델·DTO 는 `data class`, 불변(`val`) 우선. 수정은 `copy` 또는 의도를 드러내는 메서드로.
- 도메인 객체의 필드는 nullable 하지 않는 것을 지향한다 — 업무적으로 없을 수 있는 값만 `?`
  ([concepts.md](concepts.md)의 규칙 참고).
- 값에 규칙이 있으면 원시 타입 대신 **VO** 로 감싼다(`OrderNumber`, `Email`). 규칙은 `init` 에서
  `requireBusiness` 로 보증한다 — 잘못된 값은 생성 자체가 불가능하다.
- 생성자 파라미터가 많으면 **named parameter** 로 생성한다. 같은 타입 필드 혼동 방지.
- 도메인 객체 생성은 의도를 드러내는 정적 팩토리(`Order.place(...)`),
  기술/표현 객체는 `from`/`of`(`OrderResponse.from(...)`).

## 의존성 주입

- **생성자 주입만** 사용한다(필드 주입 금지). Kotlin 주 생성자 + `private val`.
- `@Autowired` 를 쓰지 않는다.

## 검증·예외 관용구

- 정상 흐름에서 도달 가능한 규칙 위반: `requireBusiness(cond, errorType)` / `requireFound(value, errorType)`.
  **호출 위치는 그 규칙을 판정한 곳이다** — 값 형식·성립 조건이면 VO/개념 객체의 `init`,
  DB 를 봐야 하는 규칙이면 그 데이터를 다루는 Implement 안. Service 본문에는 쓰지 않는다
  ([layers.md](layers.md)의 Service 절, [errors.md](errors.md)의 호출 위치 절).
- 도달하면 버그인 불변식: 표준 `require`/`check` (500 fail-fast). 구분 기준은 [errors.md](errors.md).
- null 처리는 persistence 경계까지만. `?.let` 체인으로 null 을 흘려보내는 코드는 경계 위반 신호다
  ([layers.md](layers.md)).

## 주석

**주석은 최소로.** 코드로 표현할 수 있으면 코드로 표현한다. 남기는 주석은 두 종류뿐:

1. **코드가 말할 수 없는 맥락·결정**: 왜 이 방식인지, 어떤 대안을 배제했는지.
   ```kotlin
   // refresh/logout 은 AT 만료 상황에서 호출되므로 이 경로에선 AT 를 해석하지 않는다.
   ```
2. **불변식·계약 선언**: append-only, 계약 보장 주체 등.
   ```kotlin
   // 계약: userPrincipal.name = 회원 UUID 문자열 (security 모듈의 인증 필터가 보장)
   ```

금지: 다음 줄이 뭘 하는지 반복하는 주석, 변경 이력 주석("리뷰 반영"), 자명한 KDoc.

## 로깅

- 새 로깅 호출은 kotlin-logging의 `private val log = KotlinLogging.logger {}`를 파일 최상단에 두고
  `log.debug { "..." }` 람다 형태로 쓴다. 실행 모듈은 `support:logging`에서 facade를 제공받고, facade만 필요한
  모듈은 직접 의존한다. 기존 SLF4J 호출도 같은 Logback 출력 정책을 거친다.
- 메시지는 **고정 접두어 + key=value**로 쓴다. 예: `log.debug { "room.create memberId=$memberId roomId=$roomId" }`.
  접두어는 `도메인.동작[.결과]` 소문자이며 grep 기준이 된다. 식별자(UUID·숫자 ID)·enum·개수는 넣어도 된다.
- 메시지·인자·MDC에 DTO·Entity·토큰·이메일·요청 본문·자유 입력 원문을 넣지 않는다. formatter는 메시지를 그대로 출력하며
  `Bearer`·JWT 형태만 방어적으로 가린다. 이 규칙은 리뷰에서 확인한다.
- 수준: HTTP 요청 단위 완료 로그는 공통 필터가 남기므로 Controller에 시작·종료 로그를 넣지 않는다. 상태 변경 Service의
  진입, Implement(Finder·Manager·Validator 등) 공개 메서드의 진입, HTTP 밖 경계(outbox·scheduler·worker)는 DEBUG,
  배치 결과 요약은 INFO, 예외는 ErrorType의 `logLevel`을 따른다. DEBUG는 local·dev에서 켜지고 staging·live는 INFO다.
- 접두어 규칙: Service는 `도메인.동작` (`room.create`), Implement는 `개념.역할.메서드`
  (`room.manager.create`, `participation.validator.validateHost`). 역할은 클래스명 마지막 단어의 소문자다.
  예외 어드바이스·핸들러는 `exception.<계층>[.<결과>]` (`exception.core code=E1001`, `exception.transport type=...`,
  `exception.async.unhandled type=...`)이며 예외 객체를 항상 함께 넘긴다.
- 외부 계정 식별자(`providerId`)·이메일·닉네임·자유 입력·자격 증명은 식별자여도 넣지 않는다.
- 프레임워크·드라이버 예외의 `e.message`를 메시지에 넣지 않는다. 거부된 입력값이나 DB 값이 섞인다. 예외 객체를
  `log.warn(e) { ... }`로 넘기면 formatter가 타입·스택을 남기고 `SafeLogMessage`를 구현한 예외의 메시지만 보존한다.
- 주기 실행(scheduler·worker)의 매 tick 로그는 남기지 않는다. 처리할 대상이 있을 때 건수나 건별로 남긴다.
- 예외 로깅 레벨은 ErrorType 의 `logLevel` 이 결정한다(어드바이스에서 분기). 개별 코드에서
  같은 예외를 중복 로깅하지 않는다.

## 기타

- 메서드명은 동사로 시작(`updateProfile`, `suggestNickname`). Boolean 판정은 `is*`/`exists*`/`has*`.
- 매직 넘버·매직 문자열은 상수화한다(`const val`, `companion object`).
- 시간은 호출부에서 주입받거나 고정 값으로 다룬다. 도메인 로직 안에서 `LocalDateTime.now()` 를
  직접 부르는 것을 지양한다(테스트 재현성).
