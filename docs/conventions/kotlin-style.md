# Kotlin 코드 스타일

[← 허브로](README.md)

ktlint(`INTELLIJ_IDEA` code style)를 강제한다. 설정은 [.editorconfig](../../.editorconfig) —
trailing comma 허용, star import 금지, function-expression-body 룰 비활성.
아래는 린트가 못 잡는 이 레포의 규칙이다.

## 파일 구성

- **도메인 객체는 파일당 1클래스.** 한 코틀린 파일에 도메인 클래스 두 개를 넣지 않는다
  (`JobGroup.kt` 와 `JobRole.kt` 는 별개 파일). ktlint 규칙상 파일명은 클래스명과 일치.
- **DTO 는 예외**: 응답 구조상 강결합된 하위 DTO 는 같은 파일에 둘 수 있다 ([api-design.md](api-design.md)).
- Mapper 처럼 상태 없는 변환기는 `object` 로 선언한다(빈으로 만들지 않는다).

## 클래스·값

- 도메인 모델·DTO 는 `data class`, 불변(`val`) 우선. 수정은 `copy` 또는 의도를 드러내는 메서드로.
- 도메인 객체의 필드는 nullable 하지 않는 것을 지향한다 — 업무적으로 없을 수 있는 값만 `?`
  ([concepts.md](concepts.md)의 규칙 참고).
- 값에 규칙이 있으면 원시 타입 대신 **VO** 로 감싼다(`Nickname`, `Email`). 규칙은 `init` 에서
  `requireBusiness` 로 보증한다 — 잘못된 값은 생성 자체가 불가능하다.
- 생성자 파라미터가 많으면 **named parameter** 로 생성한다. 같은 타입 필드 혼동 방지.
- 도메인 객체 생성은 의도를 드러내는 정적 팩토리(`Member.register(...)`),
  기술/표현 객체는 `from`/`of`(`ProfileResponse.from(...)`).

## 의존성 주입

- **생성자 주입만** 사용한다(필드 주입 금지). Kotlin 주 생성자 + `private val`.
- `@Autowired` 를 쓰지 않는다.

## 검증·예외 관용구

- 정상 흐름에서 도달 가능한 규칙 위반: `requireBusiness(cond, errorType)` / `requireFound(value, errorType)`.
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

- SLF4J: `private val log: Logger = LoggerFactory.getLogger(javaClass)`.
- 예외 로깅 레벨은 ErrorType 의 `logLevel` 이 결정한다(어드바이스에서 분기). 개별 코드에서
  같은 예외를 중복 로깅하지 않는다.

## 기타

- 메서드명은 동사로 시작(`createProfile`, `suggestNickname`). Boolean 판정은 `is*`/`exists*`/`has*`.
- 매직 넘버·매직 문자열은 상수화한다(`const val`, `companion object`).
- 시간은 호출부에서 주입받거나 고정 값으로 다룬다. 도메인 로직 안에서 `LocalDateTime.now()` 를
  직접 부르는 것을 지양한다(테스트 재현성).
