# Moimyeon Backend Convention

이 문서는 moimyeon-backend 의 아키텍처·코드 컨벤션의 **허브**다.
전반적인 스타일만 여기 요약하고, 영역별 상세 규칙은 링크된 문서에서 정의한다.

> 여기는 **구속력 있는 규칙**이다(위반하면 리뷰·게이트가 막는다). 판단을 돕는 경험·지식은
> [`docs/knowledge/`](../knowledge/README.md)에 있고, 그쪽에서 규칙으로 굳은 것이 여기로 승격된다.

> 문서보다 **실제 코드가 진실**이다. 코드와 문서가 다르면 코드를 기준으로 문서를 고친다.
> 아직 합의되지 않았거나 후속 과제인 항목은 각 문서에 `미확정` 으로 명시한다.

---

## 0. 기술 스택

| 항목 | 값 |
| --- | --- |
| 언어 | Kotlin `2.3` (JVM 25) |
| 빌드 | Gradle 9.5 (Kotlin DSL), 멀티모듈 |
| 프레임워크 | Spring Boot `4.1` (Spring Framework 7), Spring Cloud `2025.1` |
| 영속성 | Spring Data JPA (Hibernate), MySQL 8 / H2 `MODE=MySQL`(local·test) |
| JSON | Jackson 3 (`tools.jackson`) |
| 린트 | ktlint (`INTELLIJ_IDEA` code style, [.editorconfig](../../.editorconfig)) |
| 테스트 | JUnit5, AssertJ, MockK, springmockk |
| API 문서 | Spring REST Docs + restdocs-api-spec → openapi3.yaml (Swagger UI) |
| 루트 패키지 | `io.plady.moimyeon` |

- `-Xjsr305=strict` 로 플랫폼 타입 null 안정성을 강제한다.
- trailing comma 허용, import on-demand(`*`) 금지 (ktlint 설정으로 강제).

## 1. 한 장 요약

- **모듈**: 실행 모듈은 `core:core-api`(+`core:core-batch`) 뿐. `admin:admin-api` 는 별도 배포 없이
  core-api 프로세스에 **runtimeOnly 로 함께 조립**되고, 서로의 코드는 컴파일 타임에 모른다.
  인증 기술(spring-security)은 security 모듈에 격리되어 api 코드에는 등장하지 않는다.
  → [modules.md](modules.md)
- **도메인**: `core.domain` 은 도메인 영역별 패키지(예: `order/`, `product/`)로 나눈다.
  영역끼리는 서로에 대해 아는 것을 최소로 유지한다 — 한쪽이 상대의 id 만 알고, 역방향은
  필요해질 때까지 만들지 않는다.
  → [concepts.md](concepts.md)
- **레이어**: Controller(DTO 변환) → Service(비즈니스 흐름) → Implement(Finder/Validator/Manager 등
  재사용 로직·저장소 접근) → Repository. 여러 Service 의 결과를 조합하는 응답은 컨트롤러가 아니라
  **Facade**(`core.api.facade`)가 조립한다. 핵심은 두 가지 — **Service 를 읽으면 비즈니스 흐름이
  보여야 하고, Service 는 JPA 엔티티를 직접 다루지 않는다.** Implement 레이어는 이를 위한
  권장 패턴이지 강제가 아니다.
  **Service 본문에 들어가는 것은 셋뿐이다 — 검증 도구 호출 + 외부 I/O + 쓰기 호출.** 판정은
  도구가 하고 예외도 도구가 던진다(Service 에 `requireBusiness` 를 쓰지 않는다). 도구 이름은
  요구사항을 자연어로 풀어낸 뒤 그 역할에서 가져오고, 접미사 표는 보조 사전으로만 쓴다.
  기술 메커니즘보다 개념적인 도구가 드러나야 한다(`MemberRegistrar.register` ○ /
  `MemberRegistrationCommitter.commit` ✗). 트랜잭션은 접미사가 아니라 실제 커밋 단위를 소유한
  메서드에 붙인다.
  → [layers.md](layers.md)
- **에러**: 도메인 규칙 위반은 `CoreException(CoreErrorType)`, 요청 형태·인증 오류는
  `CoreApiException(CoreApiErrorType)`. 에러 코드(`ErrorCode`)는 하나의 체계를 공유하고 테스트로
  일관성을 강제한다. FE 분기 기준은 HTTP 상태가 아니라 `error.code`.
  → [errors.md](errors.md)
- **API**: URI 는 `/v1/{복수형 리소스}`, 요청은 `XXXRequest.toXxx()`, 응답은 `XXXResponse.from/of(...)`.
  **Bean Validation 을 쓰지 않는다** — 경계를 넘는 순간 온전한 개념 객체여야 하므로, 값 규칙은
  요청 DTO 의 `toXxx()`(형식·범위)와 값 객체 생성 시점(도메인 규칙)에서 확정한다. 필드 간·참조
  관계 규칙은 개념 객체 `init`, DB 를 봐야 하는 규칙은 그 데이터를 다루는 쓰기 Implement 가
  Repository 를 직접 보고 판정한다(다른 개념의 판정을 물어야 할 때만 그 개념의 `Validator`).
  스펙은 RestDocs 테스트가 문서로 만든다(요청 DTO 단위 테스트를 따로 두지 않는다).
  → [api-design.md](api-design.md)
- **인증**: 컨트롤러는 `@LoginMember CurrentMember` 로 주입받는다. api 와 security 의 접점은
  표준 `Principal` 하나뿐이고, security 가 필요로 하는 기능은 인터페이스로 선언해 core-api 가
  구현을 제공한다.
  → [auth.md](auth.md)
- **스토리지**: 스키마의 단일 소스는 `schema.sql`(엔티티는 매핑만). 참조 데이터는 `seed.sql`,
  로컬 개발 데이터는 `data-local.sql`(docker initdb 전용). 표준 베이스(`BaseEntity`/`UuidBaseEntity`)는
  소프트 삭제(`deleted_at` 컬럼 방식)를 내장하며, 별도 라이프사이클이 있는 엔티티는 베이스를 상속하지 않고
  id·시간을 직접 선언한다. 탈퇴 회원은 조회 시점(Finder)에 걸러 도메인 로직에 들어오지 않게 한다.
  → [storage.md](storage.md)
- **테스트**: 태그 4종(`unitTest`/`contextTest`/`restDocsTest`/`developTest`)으로 분리 실행.
  단위는 MockK, 통합은 `ContextTest`/`CoreDbContextTest`(생성자 주입), 문서화는 `RestDocsTest`(standalone).
  **PR CI 가 돌리는 것은 `test` 뿐이다** — `develop` 과 `restdocs` 는 거기서 제외되므로,
  회귀를 막을 검증은 `develop` 태그에 두지 않는다.
  → [testing.md](testing.md)
- **API 문서화**: 문서는 테스트가 만든다. 성공뿐 아니라 **예외 케이스도 에러 코드와 함께** 문서화 테스트로 정의하고,
  openapi3.yaml 의 4xx 응답·코드별 예시로 병합된다.
  → [api-docs.md](api-docs.md)
- **코틀린 스타일**: 도메인 객체는 파일당 1클래스(DTO 는 예외), data class·val·named parameter,
  주석은 코드로 표현할 수 없는 배경 설명과 지켜야 할 규칙만.
  → [kotlin-style.md](kotlin-style.md)
- **Git**: Angular 커밋 컨벤션(제목 한글 허용, 선택적 맥락·TODO 본문, em-dash `—` 금지,
  co-author 트레일러 금지), 작업 단위로 응집된 커밋 + 커밋별 빌드 보장, ASCII 브랜치명,
  PR 은 커밋 본문의 맥락을 취합하고 `Closes MOI-xxx` 포함.
  → [git.md](git.md)

## 2. 문서 목록

| 문서 | 내용 |
| --- | --- |
| [modules.md](modules.md) | 멀티 모듈 구조, 의존 방향, admin 런타임 조립, 모듈별 책임 |
| [concepts.md](concepts.md) | 도메인 영역 나누기: 패키지 구조, 영역 간 참조 규칙, 파생 값은 저장하지 않기 |
| [layers.md](layers.md) | 레이어별 역할과 스타일: Controller/Service/Implement, null·엔티티 차단 규칙, 트랜잭션 |
| [errors.md](errors.md) | 예외 계층(api/도메인), 에러 코드 체계, 어드바이스, 유니크 충돌 매핑 |
| [api-design.md](api-design.md) | URI·DTO 네이밍·변환·검증·응답 포맷·모킹 API 패턴 |
| [auth.md](auth.md) | 인증 구조: 표준 Principal 로 최소화한 api-security 접점 |
| [storage.md](storage.md) | 스키마 관리(schema.sql), 엔티티 스타일, 시딩 체계, 참조 데이터 |
| [testing.md](testing.md) | 테스트 분류(태그), 베이스 클래스, 레이어별 전략, 작성 스타일 |
| [api-docs.md](api-docs.md) | RestDocs → openapi3 파이프라인, 예외 케이스 문서화 방법 |
| [kotlin-style.md](kotlin-style.md) | 파일 구성, 네이밍, 주석, 검증 함수(requireBusiness 등), 로깅 |
| [git.md](git.md) | 브랜치·커밋 메시지·PR·리뷰 워크플로 |
