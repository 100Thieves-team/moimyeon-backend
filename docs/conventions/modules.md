# 모듈 구조

[← 허브로](README.md)

```
moimyeon/
├── admin/
│   └── admin-api        어드민 API. core-api 런타임에 조립되는 비부트 모듈
├── batch/
│   └── batch-app        배치 실행 모듈 (독립 bootJar)
├── core/
│   ├── core-enum        도메인 전역 공유 Enum 만 격리 (최하위 모듈)
│   └── core-api         API 서버 실행 모듈 (bootJar). 도메인 + api + support
├── security/
│   └── security-core    Spring Security 필터 체인·JWT·OAuth (인증 기술 격벽)
├── storage/
│   └── db-core          JPA Entity / Repository / schema.sql (RDB 접근)
├── clients/
│   └── client-example   외부 HTTP 클라이언트 (HTTP Interface)
├── support/
│   ├── logging          로깅 설정(yml)
│   └── monitoring       모니터링 설정(yml)
└── tests/
    └── api-docs         RestDocs 테스트 베이스 (테스트 전용 공유 모듈)
```

## 의존 규칙

- 실행 가능한 산출물(bootJar)은 `core:core-api` 와 `batch:batch-app` 뿐이다. 나머지는 `jar`.
- 컴파일 타임 의존 방향:
  - `core-api` → `core-enum`, `security:security-core`, `storage:db-core`, `clients:*`, `support:*`
  - `core-api` → `admin:admin-api` 는 **`runtimeOnly`** (아래 참고)
  - `storage:db-core` → `core-enum`
  - `security:security-core` → `core-enum`
- `core-enum` 은 어떤 모듈에도 의존하지 않는다. 순환 의존을 막는 최하위 공유 모듈.
- `storage:db-core` 는 JPA starter 를 노출해 core-api 의 Implement 레이어가 Repository 인터페이스를
  직접 주입받는다. 단, **Entity 는 Implement(Mapper) 밖으로 나가지 않는다** ([layers.md](layers.md)).
- 설정은 `spring.config.import` 로 모듈별 yml(`db-core.yml`, `security-core.yml`, `logging.yml`,
  `monitoring.yml`, `client-example.yml`)을 합성한다. 프로파일: `local`, `local-dev`, `dev`, `live`
  (+`test` 는 프로파일 그룹으로 local 상속 — [storage.md](storage.md)).

## admin-api: 런타임 조립

어드민은 **별도 배포 없이 core-api 프로세스에 함께 뜨지만, core 와 컴파일 타임에 서로를 모른다.**

- `core-api` 가 `runtimeOnly(project(":admin:admin-api"))` 로만 의존한다.
  admin 컨트롤러·서비스는 컴포넌트 스캔으로 발견될 뿐, core 코드에서 admin 타입을 참조할 수 없다(역방향도 동일).
- admin 의 모든 클래스는 `Admin` 접두사를 붙인다(`AdminExampleService`, `AdminControllerAdvice`).
  같은 프로세스에 조립되므로 빈 이름·클래스명 충돌을 접두사로 예방한다.
- 전역 어드바이스 충돌 방지: `ApiControllerAdvice` 는 `basePackages = ["io.plady.moimyeon.core"]`,
  admin 은 자체 `AdminControllerAdvice` 를 갖는다.
- admin 이 커지거나 배포 주기가 달라지면 독립 bootJar 로 분리한다(그 전까지는 조립 유지).

## security-core: 인증 기술 격벽

api(core-api)가 spring-security 에 오염되지 않도록 인증 기술을 모듈로 격리한다.
core-api 는 security-core 를 의존하지만, **api 패키지에는 spring-security 타입이 등장하지 않는다**
(브리지는 표준 `Principal` 하나 — [auth.md](auth.md)).

## clients: 외부 연동

- 외부 HTTP 연동은 `clients:{대상}` 모듈로 분리한다. Spring HTTP Interface(`ExampleApi`) +
  구현 클라이언트(`ExampleClient`) + 전용 요청/응답 DTO 구성.
- 클라이언트의 DTO 는 외부 와이어 포맷 표현이므로 core 도메인 객체와 분리한다.

## tests/api-docs: 테스트 전용 공유 모듈

- `RestDocsTest` 베이스 클래스와 restdocs-api-spec 의존성을 담는다. 각 모듈이
  `testImplementation(project(":tests:api-docs"))` 로 가져다 쓴다 ([testing.md](testing.md), [api-docs.md](api-docs.md)).
