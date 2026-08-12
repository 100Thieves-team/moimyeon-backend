# 테스트

[← 허브로](README.md)

## 분류: 태그 기반 선택 실행

JUnit5 `@Tag` 로 구분하고 Gradle 태스크로 선택 실행한다 (루트 `build.gradle.kts`).

| 태스크 | 대상 | 용도 | PR CI |
| --- | --- | --- | --- |
| `test` (기본) | `develop`·`restdocs` 제외 전부 | CI 기본 | ✅ |
| `unitTest` | `develop`·`context`·`restdocs` 제외 | 순수 단위 테스트만 빠르게 | (test 에 포함) |
| `contextTest` | `@Tag("context")` | 스프링 컨텍스트 통합 테스트 | (test 에 포함) |
| `restDocsTest` | `@Tag("restdocs")` | API 문서화 테스트 (`asciidoctor`/`openapi3` 가 의존) | ❌ |
| `developTest` | `@Tag("develop")` | **개발 환경 전용** | ❌ |

**어디서 도는지가 곧 그 테스트의 값이다.**

- **`context` 는 CI 에서 돈다.** PR 과 배포의 관문이므로 머신을 가리지 않고 재현돼야 한다.
  기본은 인메모리 H2 + `test` 프로파일이며 팀이 관리하는 공유 외부 자원에 기대지 않는다.
  단, H2가 재현하지 못하는 MySQL 방언·Flyway·Hibernate 타입 계약은 운영과 같은 버전을 고정한
  Testcontainer를 테스트가 직접 띄워 검증한다. 컨테이너 밖의 영속 상태에는 의존하지 않는다.
- **`develop` 은 CI 에서 돌지 않는다.** GitHub Actions 는 `test` 태스크만 실행하고 이 태그는
  거기서 제외된다. 개발자 로컬에서 실제 개발 환경을 붙여 확인하는 용도다.
  **회귀를 막을 목적의 검증을 이 태그에 두지 않는다** — 아무도 실행하지 않는 테스트가 된다.
- **`restdocs` 도 PR CI 에서 돌지 않는다.** 문서 발행 워크플로가 `dev`/`main` push 때 돌린다.
  즉 API 스펙 검증은 **머지 이후에** 처음 실행된다. 스펙을 깨는 변경이 PR 을 통과할 수 있으므로,
  요청 DTO 값 규칙을 고쳤으면 로컬에서 `restDocsTest` 를 직접 돌려 확인한다.

컨텍스트 테스트는 환경 변수(`JWT_SECRET` 32바이트+, `GOOGLE_OAUTH_CLIENT_ID/SECRET`)가 필요하다.

## 베이스 클래스

| 베이스 | 구성 | 용도 |
| --- | --- | --- |
| `ContextTest` (core-api) | `@SpringBootTest` + `@ActiveProfiles("test")` + `@TestConstructor(ALL)` + `@Tag("context")` | 도메인 통합 테스트 (`XxxServiceIT`) |
| `CoreDbContextTest` (db-core) | 동일 구성 | Repository 통합 테스트 (`XxxRepositoryIT`) |
| `RestDocsTest` (tests/api-docs) | standalone MockMvc + `@Tag("restdocs")` | 컨트롤러 문서화 테스트 ([api-docs.md](api-docs.md)) |
| `DevelopTest` (core-api) | `@SpringBootTest` + `@Tag("develop")`, **프로파일 지정 없음** | 개발 환경을 붙여 눈으로 확인할 때만. CI 에서 실행되지 않는다 |

- 통합 테스트 빈 주입은 **생성자 주입**(`@TestConstructor(ALL)`).
- `test` 프로파일은 local 상속이라 H2 + schema.sql + seed.sql 위에서 돈다 ([storage.md](storage.md)).

## 레이어별 전략

| 대상 | 방식 | 무엇을 보는가 |
| --- | --- | --- |
| Service 흐름 | 순수 단위 테스트. 스프링 없이 **MockK** 로 도구 mock | 도구 호출 순서와 예외 전파 |
| 규칙 판정 | 쓰기 Implement 안이면 그 Implement 테스트로, 별도 도구면 그 도구 테스트로 | 규칙 자체가 맞는지 |
| 조합·예외 번역 | 순수 단위 테스트. 예외를 주입해 매핑·전파·재시도를 확인 | 어떤 충돌이 어떤 도메인 에러가 되는지 |
| 도메인 VO | 순수 단위 테스트 | 형식·길이·금칙 규칙이 생성 시점에 걸리는지 |
| 도메인 흐름 (DB 포함) | `ContextTest` 상속 + 실제 빈·H2 | 여러 쓰기가 한 커밋으로 묶이는지 |
| Repository | `CoreDbContextTest` 상속 + `@Transactional` | 파생 쿼리·유니크 제약 |
| 어드바이스 계약 | standalone MockMvc + mockk Service + 어드바이스 | 본문 해석 실패·파라미터 누락·타입 불일치가 400(E400)인 계약 |
| **API 스펙** (요청 DTO 값 규칙) | `RestDocsTest` 상속 — 실제로 깨진 요청을 보낸다 | 값 규칙 위반이 문서화된 4xx 로 나가는지 |
| 컨트롤러 (문서화) | `RestDocsTest` 상속 | 성공·예외 케이스가 스니펫으로 남는지 |
| 아키텍처 가드 | 순수 단위 테스트 | 에러 코드 합집합처럼 코드로 강제 못 하는 규약 |

- 단위/통합의 기준: **규칙과 흐름은 mockk 단위로**, DB 제약·쿼리·트랜잭션이 얽힌 행위는 IT 로.
  같은 것을 두 층에서 반복 검증하지 않는다.
- **요청 DTO 단위 테스트를 따로 만들지 않는다.** 값 규칙은 API 스펙이므로 RestDocs 테스트에서
  실제로 깨진 요청을 보내 검증한다 — 그래야 openapi3.yaml 의 4xx 예시로 나간다
  ([api-docs.md](api-docs.md)). DTO 를 직접 호출하는 테스트를 덧붙이면 같은 규칙을 두 층에서
  검증하면서 문서에는 실리지 않는다.
- Service 단위 테스트는 **도구를 mock 해 흐름만** 본다 — 검증 실패 케이스는 도구가
  `CoreException` 을 던지도록 스텁하고 그것이 그대로 전파되는지 확인한다. 규칙 자체가 맞는지는
  그 도구의 테스트에서 본다. 같은 규칙을 Service 와 도구 양쪽에서 검증하지 않는다.
  ```kotlin
  every { orderManager.place(userId, content) } throws CoreException(CoreErrorType.OUT_OF_STOCK)
  ```
- 동시성 레이스 자체는 테스트로 재현하지 않는다. 검증 대상은 레이스의 **결과 예외를 다루는 방식**
  (매핑·전파·재시도)이며, 예외 번역을 소유한 Implement 의 단위 테스트에서 예외 주입으로 확인한다
  ([errors.md](errors.md)의 번역 위치 규칙과 짝을 이룬다).
  - 제약명별로 갈리는지 본다 — 재생성 가능한 값의 충돌은 재시도, 그 외는 도메인 에러.
  - **기대하지 않은 무결성 위반이 전파되는지도 테스트한다** — 오인 매핑을 막는 것이 이 규칙의
    핵심이므로, not-null 위반 같은 무관한 예외를 주입해 그대로 500 이 되는지 확인한다.

### 트랜잭션 경계를 검증할 때

테스트 클래스나 메서드의 `@Transactional` 은 데이터 정리에는 편하지만, 운영 코드의 Spring 프록시
경계와 커밋·롤백 시점을 가릴 수 있다. **원자성, 롤백 분리, 재시도를 검증하는 통합 테스트에는 바깥
테스트 트랜잭션을 두지 않는다.**

- 실제 상위 public 메서드를 호출해 운영 코드와 같은 프록시 경로를 탄다.
- 실패가 반환된 뒤 별도 Repository 조회로 결과를 확인한다.
- 여러 쓰기가 한 커밋이라면 뒤 단계 실패 시 앞 단계도 남지 않는지 all-or-nothing을 단언한다.
  예: 프로필 생성이 실패하면 회원과 필수 약관 동의도 남지 않는다.
- 재시도 흐름은 실패한 시도의 데이터가 롤백된 뒤 다음 시도가 독립적으로 성공하는지 확인한다.

Repository 쿼리 자체를 검증하거나 테스트 데이터 정리만 필요한 경우에는 기존처럼 테스트 수준의
`@Transactional` 을 사용해도 된다.

## 작성 스타일

- 테스트 이름은 **한글 백틱 함수명**으로 행위를 문장으로 쓴다.
  ```kotlin
  @Test
  fun `동시 요청이 아닌 무결성 위반은 유니크 충돌로 오인하지 않고 전파한다`() { ... }
  ```
  에러 케이스는 코드도 함께 적는다: `` `이미 사용 중인 값으로 변경하면 E1007 을 던진다` ``.
- 단언은 AssertJ(`assertThat`). 예외는 `assertThatThrownBy` + `isInstanceOfSatisfying` 으로
  errorType 까지 확인한다.
- `// given / // when / // then` 주석은 단계가 길 때만 쓴다(짧은 테스트에는 강제하지 않는다).
- 반복되는 스텁 구성은 `givenXxx()` 헬퍼로, 반복 단언은 `assertXxxFails(errorType)` 헬퍼로 모은다.
- 통합 테스트에서 참조 id 가 실제 존재하는 데이터가 되도록 부모 데이터를 만드는 픽스처는
  `persistXxx()` 헬퍼로 테스트 파일 안에 둔다. 공유 픽스처 모듈은 만들지 않는다(테스트가 스스로
  데이터를 만든다).
- 시간은 `LocalDateTime.of(...)` 고정 값을 쓴다(`now()` 금지 — 재현성).
