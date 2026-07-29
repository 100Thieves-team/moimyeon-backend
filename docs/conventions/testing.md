# 테스트

[← 허브로](README.md)

## 분류: 태그 기반 선택 실행

JUnit5 `@Tag` 로 구분하고 Gradle 태스크로 선택 실행한다 (루트 `build.gradle.kts`).

| 태스크 | 대상 | 용도 |
| --- | --- | --- |
| `test` (기본) | `develop`·`restdocs` 제외 전부 | CI 기본 |
| `unitTest` | `develop`·`context`·`restdocs` 제외 | 순수 단위 테스트만 빠르게 |
| `contextTest` | `@Tag("context")` | 스프링 컨텍스트 통합 테스트 |
| `restDocsTest` | `@Tag("restdocs")` | API 문서화 테스트 (`asciidoctor`/`openapi3` 가 의존) |
| `developTest` | `@Tag("develop")` | 개발 중 임시 |

컨텍스트 테스트는 환경 변수(`JWT_SECRET` 32바이트+, `GOOGLE_OAUTH_CLIENT_ID/SECRET`)가 필요하다.

## 베이스 클래스

| 베이스 | 구성 | 용도 |
| --- | --- | --- |
| `ContextTest` (core-api) | `@SpringBootTest` + `@ActiveProfiles("test")` + `@TestConstructor(ALL)` + `@Tag("context")` | 도메인 통합 테스트 (`XxxServiceIT`) |
| `CoreDbContextTest` (db-core) | 동일 구성 | Repository 통합 테스트 (`XxxRepositoryIT`) |
| `RestDocsTest` (tests/api-docs) | standalone MockMvc + `@Tag("restdocs")` | 컨트롤러 문서화 테스트 ([api-docs.md](api-docs.md)) |

- 통합 테스트 빈 주입은 **생성자 주입**(`@TestConstructor(ALL)`).
- `test` 프로파일은 local 상속이라 H2 + schema.sql + seed.sql 위에서 돈다 ([storage.md](storage.md)).

## 레이어별 전략

| 대상 | 방식 | 예 |
| --- | --- | --- |
| Service 규칙·흐름 | 순수 단위 테스트. 스프링 없이 **MockK** 로 협력 객체 mock | `ProfileServiceTest` — 검증 순서·에러 매핑·전파 |
| 도메인 VO | 순수 단위 테스트 | `NicknameTest` |
| 도메인 흐름 (DB 포함) | `ContextTest` 상속 + 실제 빈·H2 + `@Transactional` | `ProfileServiceIT`, `SocialAuthServiceIT` |
| Repository | `CoreDbContextTest` 상속 + `@Transactional` | `MemberProfileRepositoryIT` — 파생 쿼리·유니크 제약 |
| 컨트롤러 (계약) | standalone MockMvc + mockk Service + 어드바이스 | `ProfileControllerValidationTest` — 검증 실패·본문 해석 실패가 400(E400)인 계약 |
| 컨트롤러 (문서화) | `RestDocsTest` 상속 | `ProfileControllerTest` — 성공·예외 케이스 문서화 |
| 아키텍처 가드 | 순수 단위 테스트 | `ErrorTypeConsistencyTest` — 에러 코드 합집합 검증 |

- 단위/통합의 기준: **규칙과 흐름은 mockk 단위로**, DB 제약·쿼리·트랜잭션이 얽힌 행위는 IT 로.
  같은 것을 두 층에서 반복 검증하지 않는다.
- 동시성 레이스 같은 재현 불가 경로는 단위 테스트에서 예외 주입으로 검증한다
  (예: `profileManager.append(...) throws DataIntegrityViolationException` → 매핑/전파 확인).

## 작성 스타일

- 테스트 이름은 **한글 백틱 함수명**으로 행위를 문장으로 쓴다.
  ```kotlin
  @Test
  fun `동시 요청이 아닌 무결성 위반은 유니크 충돌로 오인하지 않고 전파한다`() { ... }
  ```
  에러 케이스는 코드도 함께 적는다: `` `이미 프로필이 있으면 E1008 을 던진다` ``.
- 단언은 AssertJ(`assertThat`). 예외는 `assertThatThrownBy` + `isInstanceOfSatisfying` 으로
  errorType 까지 확인한다.
- `// given / // when / // then` 주석은 단계가 길 때만 쓴다(짧은 테스트에는 강제하지 않는다).
- 반복되는 스텁 구성은 `givenXxx()` 헬퍼로, 반복 단언은 `assertXxxFails(errorType)` 헬퍼로 모은다.
- 통합 테스트에서 참조 id 가 실제 존재하는 데이터가 되도록 부모 데이터를 만드는 픽스처는
  `persistXxx()` 헬퍼로 테스트 파일 안에 둔다. 공유 픽스처 모듈은 만들지 않는다(테스트가 스스로
  데이터를 만든다).
- 시간은 `LocalDateTime.of(...)` 고정 값을 쓴다(`now()` 금지 — 재현성).
