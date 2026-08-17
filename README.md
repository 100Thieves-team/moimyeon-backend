# Moimyeon Backend

Spring Boot (Kotlin) 기반 멀티모듈 프로젝트.

> 아키텍처·코드 컨벤션은 [docs/conventions](docs/conventions/README.md) 에서 관리한다.

## 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Kotlin 2.3 / Java 25 |
| Framework | Spring Boot 4.1 |
| Build | Gradle 9.5 (Kotlin DSL) |
| DB | MySQL (local 은 H2) |

---

## 모듈 구조

```
moimyeon/
├── admin/
│   └── admin-api       어드민 API — core-api 런타임에 조립되는 비부트 모듈
│
├── core/
│   ├── core-batch      배치 실행 모듈 (독립 bootJar)
│   ├── core-enum       공통 Enum 정의
│   └── core-api        API 서버 실행 모듈 — 외부 연동 계약과 런타임 조립 소유
│
├── security/
│   └── security-core   인증/인가 — Google OAuth · JWT 세션 · 로그인 회원 주입
│
├── storage/
│   ├── db-core         JPA/MySQL 영속성 — DataSource · JPA 설정 소유
│   └── object-storage  AWS SDK S3 객체 저장·조회
│
├── support/
│   ├── logging         환경별 Logback 설정 (OpenTelemetry · Sentry)
│   └── monitoring      Actuator + Prometheus
│
├── clients/
│   ├── bedrock-client  Spring AI · Bedrock 모델 클라이언트
│   └── client-example  OpenFeign 기반 외부 HTTP 클라이언트 예시
│
└── tests/
    └── api-docs        Spring REST Docs 지원
```

---

## 모듈별 상세

### `core:core-enum`
모든 모듈에서 공유하는 Enum 클래스를 정의한다. 외부 모듈에 노출되어야 하는 열거형만 포함한다.

- 의존성: 없음

---

### `core:core-api`
API 서버 실행 모듈. REST API 레이어와 도메인 서비스를 담당한다.

- 의존성 (compile): `core-enum`, `security/security-core`, `storage/db-core`, `support/*`, `clients/client-example`, `spring-boot-starter-webmvc`
- 의존성 (runtime): `admin/admin-api`, `clients/bedrock-client`, `storage/object-storage`
- 포함: Controller, Request/Response DTO, 도메인 서비스, 이력서 영역의 외부 연동 계약, `ApiControllerAdvice`, AsyncConfig
- 산출물: 실행용 `bootJar`와 저수준 모듈의 계약 참조용 plain `jar`

> admin 모듈은 `runtimeOnly`로 선언되어 core와 컴파일 시점에 격리된다. `ApiControllerAdvice`는 `basePackages = ["io.plady.moimyeon.core"]`로 범위를 제한해, 함께 조립되는 admin 컨트롤러의 예외를 가로채지 않는다.

---

### `admin:admin-api`
운영용 어드민 API. 자체 실행되지 않고 core-api 서버가 뜰 때 런타임으로 조립된다.

- 의존성 (compileOnly): `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`
- 의존성 (test): 위 두 스타터 + `tests/api-docs` (testImplementation), H2 (testRuntimeOnly)
- 포함: `admin.{config, controller, domain, support}` 패키지, `AdminProviderArgumentResolver`, 어드민 전용 엔티티

> **격리 원칙.** admin 과 core 는 컴파일 타임에 서로를 전혀 모른다. 어드민 도메인 객체·ControllerAdvice·설정을 전부 자체 보유하며, core 타입을 일절 참조하지 않는다. 스타터가 `compileOnly`인 이유는 런타임 공급자가 core-api 이기 때문이다 (`compileOnly`는 테스트 클래스패스에 전파되지 않으므로 test 쪽에 별도 선언).

> **엔티티 스캔 트릭.** 어드민 엔티티는 admin 모듈 안의 `io.plady.moimyeon.storage.db.core` 패키지(split package)에 둔다. 패키지 스캔은 jar 경계와 무관하므로, db-core 의 `@EntityScan`/`@EnableJpaRepositories` 범위에 명시적 추가 없이 그대로 잡혀 단일 EntityManagerFactory 로 로드된다. db-core 는 admin 의 존재를 문자열로조차 모른다.

> **네이밍 컨벤션.** admin 의 모든 클래스는 예외 없이 `Admin` 접두사를 갖는다 (`AdminErrorType`, `AdminApiResponse`, `AdminBaseEntity`, ...). 조립 런타임에서의 빈 이름 충돌을 원천 차단하고, 클래스명만으로 소속을 구분하기 위함이다.

> **분리 시나리오.** 어드민 안정성·트래픽 격리가 필요해지면 admin-api 에 Application 클래스를 추가하고 core-api 의 `runtimeOnly` 한 줄을 빼는 것으로 독립 서버 전환이 가능하다.

---

### `core:core-batch`
스케줄 기반 배치 실행 모듈. core-api 와 별개의 독립 부트 앱이다.

- 의존성: `support/*`, `storage/db-core` (implementation)
- 포함: `BatchApplication`, `@EnableScheduling` 설정, `batch.job.*` 잡 클래스 (`Batch` 접두사 컨벤션)

> **admin 과 실행 모델이 다른 이유.** admin 은 요청 주도라 core-api 에 조립해도 안전하지만, 배치는 시간 주도라 조립 상태에서 core-api 를 스케일 아웃하면 같은 잡이 중복 실행된다. 그래서 처음부터 독립 부트 앱으로 분리했다. 엔티티는 격리 목적이 없으므로 db-core 를 `implementation`으로 직접 물어 재사용한다.

---

### `security:security-core`
인증/인가 담당 모듈. core-api 에 `implementation`으로 배선되어 있다. Google OAuth 로그인, 자체 JWT 세션 인증,
컨트롤러 인증 계약과 부하테스트 인증 게이트를 소유한다.

- 의존성: `spring-boot-starter-security`, `spring-boot-starter-oauth2-client`, webmvc (compileOnly — 런타임은 core-api 공급)
- 포함: `SecurityConfig`, `OAuth2LoginSuccessHandler`, JWT·쿠키 인증, `PerfAuthenticationFilter` + `PerfAuthConfig`

> **컨트롤러 인증 계약.** 컨트롤러는 `@LoginMember CurrentMember`를 받고, 표준
> `Principal.name = 회원 UUID 문자열`만 해석한다. provider ID(Google sub)는 OAuth 어댑터에서 내부 회원 UUID로
> 번역을 끝내고 컨트롤러·서비스에는 노출하지 않는다.

> **부하테스트 인증.** `PerfAuthenticationFilter`는 `X-Test-User-Id`의 회원 UUID를 신뢰해 인증을 세팅한다
> (k6/JMeter 용). 인증 우회 백도어이므로 `@Component` 자동 등록 없이 **perf 프로파일 +
> `security.perf-auth.enabled=true` 이중 게이트**를 통과해야만 빈이 생성된다. `live` 프로파일에서는 두 값을
> 넣어도 등록되지 않는다. 별도 환경은 `SPRING_PROFILES_ACTIVE=dev,perf`로 실행한다.

> **위치 선정 근거.** security 는 presentation 앞단의 횡단 관심사로, 요청을 가로채 동작을 바꾸는 능동적 컴포넌트다. 수동적 계측 인프라인 `support/*` 와 결이 달라 독립 top-level 그룹으로 둔다. 단방향 규칙: `core-api → security-core`, storage/domain 은 security 를 모르며, 서비스 레이어는 `userId`를 평범한 파라미터로 받는다.

> **레이어 경계.** 인증(authN)과 정적 경로·role 기준의 거친 인가(coarse authZ)만 필터 단계에서 처리한다. 데이터에 의존하는 세밀한 인가(소유권·도메인 상태 기반)는 service 레이어로 미룬다.

---

### `storage:db-core`
JPA/MySQL 기반 영속성 모듈. DataSource(HikariCP)와 JPA 설정을 소유한다.

- 의존성: `spring-boot-starter-data-jpa` (api), MySQL Connector/J, H2 (runtimeOnly)
- 포함: `BaseEntity`, JPA Entity, Spring Data Repository, `CoreDataSourceConfig`, `CoreJpaConfig`
- 프로파일: local 은 H2(in-memory, MODE=MySQL) + `ddl-auto: create`, dev 이상은 MySQL + `ddl-auto: validate`

> `@EntityScan`/`@EnableJpaRepositories`의 basePackages 는 `io.plady.moimyeon.storage.db.core` 하나다. admin 엔티티는 이 패키지를 공유(split package)하는 방식으로 스캔 범위에 들어오므로, db-core 설정은 다른 모듈이 늘어나도 변하지 않는다.

---

### `support:logging`
환경별 Logback 설정을 담당한다.

- 의존성: `spring-boot-starter-opentelemetry`, `sentry-logback`
- 프로파일별 설정: `logback-local` / `local-dev` / `dev` / `live` (local 계열은 `io.plady.moimyeon` DEBUG)

---

### `support:monitoring`
Spring Actuator와 Prometheus 메트릭 엔드포인트를 제공한다.

- 의존성: `spring-boot-starter-actuator`, `micrometer-registry-prometheus`

---

### `clients:client-example`
OpenFeign 기반 외부 HTTP 클라이언트 작성 예시. 새 HTTP 클라이언트 모듈의 참고 템플릿.

- 의존성: `spring-cloud-starter-openfeign`, `feign-hc5`, `feign-micrometer`

---

### `clients:bedrock-client`
`core-api`의 `ResumeSummaryGenerator`를 구현하는 Bedrock 전용 클라이언트. Spring AI를 통해
서울 리전 Bedrock Converse 엔드포인트에서 Sonnet 5 글로벌 추론 프로필을 호출하며
`core-api`에는 런타임으로만 조립된다.

> 글로벌 추론은 PDF를 한국 외 AWS 상용 리전에서 처리할 수 있다. 운영 활성화 전에 개인정보 국외 처리 고지,
> 적법한 처리 근거, 보존·삭제 기준과 조직 승인을 확정해야 하며, 승인 전에는 글로벌 프로필을 사용하지 않는다.

- 의존성: `core-api`, `spring-ai-starter-model-bedrock-converse`, `pdfbox`
- 참조 제한: `ResumeSummaryGenerator`와 `ResumeSummaryGenerationException`만 사용하며 빌드에서 검사한다.

---

### `tests:api-docs`
Spring REST Docs 기반 API 문서화를 지원하는 테스트 전용 모듈.

- 의존성: `spring-boot-restdocs`, `spring-restdocs-mockmvc`
- admin 은 `AdminRestDocsTest`(AdminProvider 리졸버 등록 헬퍼 포함)로 상속해 사용한다

---

## 의존 관계

```
core-enum ──────────────── core-api (implementation)
core-api ──────────────── bedrock-client (implementation, ResumeSummaryGenerator 계약만 참조)
core-api ──────────────── object-storage (implementation, ResumeFileStore 계약만 참조)

admin-api ──────────────── core-api (runtimeOnly)   # 컴파일 격리, 런타임 조립
bedrock-client ─────────── core-api (runtimeOnly)   # AI 계약 구현체 런타임 조립
object-storage ─────────── core-api (runtimeOnly)   # S3 계약 구현체 런타임 조립
db-core   ──────────────── core-api (implementation)
db-core   ──────────────── core-batch (implementation)

security-core ──────────── core-api (implementation)

support/logging    ─────── core-api, core-batch (implementation)
support/monitoring ─────── core-api, core-batch (implementation)
clients/client-example ─── core-api (implementation)
tests/api-docs ─────────── core-api, admin-api (testImplementation)
```

핵심 설계 원칙:
- 부트 가능한 모듈은 `core-api`(API 서버, admin 조립 호스트)와 `core-batch`(배치) 둘뿐이다.
- `admin ↔ core`는 컴파일 타임 완전 격리. 어드민은 도메인 객체·에러 체계·설정을 전부 자체 보유하고, 접점은 런타임 조립(컴포넌트 스캔 + split package 엔티티 스캔)뿐이다.
- 배치는 시간 주도 워크로드라 조립하지 않고 독립 앱으로 둔다 (스케일 아웃 시 잡 중복 방지).
- security 는 presentation 앞단 모듈로, 서비스 레이어에는 인증 컨텍스트가 아닌 평범한 값(`userId`)만 흘러 들어간다.

---

## 테스트

루트 빌드 스크립트가 태그 기반 테스트 스위트를 제공한다.

| Gradle task | 태그 | 용도 |
|-------------|------|------|
| `unitTest` | (context/develop/restdocs 제외) | 순수 유닛 테스트 |
| `contextTest` | `context` | 스프링 컨텍스트 로딩 검증 |
| `restDocsTest` | `restdocs` | REST Docs 문서 생성 (asciidoctor 가 의존) |
| `developTest` | `develop` | 개발 중 임시 테스트 |
| `test` | develop/restdocs 제외 전체 | CI 기본 |

모듈별 추상 베이스: core 는 `ContextTest`/`DevelopTest`, admin 은 `AdminContextTest`/`AdminDevelopTest`/`AdminRestDocsTest`, batch 는 `BatchContextTest`.

---

## 환경변수 요약

| 변수/프로퍼티 | 사용 모듈 | 설명 |
|------|-----------|------|
| `SPRING_PROFILES_ACTIVE` | core-api, core-batch | 기본값 `local` (H2 부팅, 외부 의존 없음) |
| `storage.database.core-db.url` / `.username` / `.password` | storage/db-core | dev 이상 MySQL 접속 정보 (외부 설정 주입) |
| `security.perf-auth.enabled` | security/security-core | `dev,perf` 전용 환경에서 회원 UUID 기반 `X-Test-User-Id` 인증 활성화 (이중 게이트, live 차단) |
| `OAUTH_FRONTEND_SUCCESS_REDIRECT_URI` | security/security-core | OAuth 성공 후 프론트 콜백 절대 URI (`live`: `https://moimyeon.plady.io/auth/callback`, `dev`: `https://dev.moimyeon.plady.io/auth/callback`) |
| `OAUTH_FRONTEND_FAILURE_REDIRECT_URI` | security/security-core | OAuth 실패 후 프론트 화면 절대 URI (`live`: `https://moimyeon.plady.io/?authError=login_failed`, `dev`: `https://dev.moimyeon.plady.io/?authError=login_failed`) |

> Google OAuth client-id/secret과 JWT secret은 실행 환경에서 주입한다.
