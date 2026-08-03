# 모듈 구조

[← 허브로](README.md)

```text
moimyeon/
├── admin/
│   └── admin-api        어드민 API. core-api 런타임에 조립되는 비부트 모듈
├── batch/
│   └── batch-app        배치 실행 모듈 (독립 bootJar)
├── core/
│   ├── core-domain      외부 구현이 따르는 프레임워크 없는 도메인 포트
│   ├── core-enum        도메인 전역 공유 Enum 만 격리 (최하위 모듈)
│   └── core-api         API 서버 실행 모듈 (bootJar). 도메인 + api + support
├── security/
│   └── security-core    Spring Security 필터 체인·JWT·OAuth (인증 기술 격벽)
├── storage/
│   ├── db-core          JPA Entity / Repository / schema.sql (RDB 접근)
│   └── object-storage   AWS SDK S3 객체 저장·조회 격벽
├── clients/
│   ├── client-ai        Spring AI · Bedrock 외부 모델 클라이언트
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
  - `core-api` → `core-domain`, `core-enum`, `security:security-core`, `storage:db-core`, `clients:client-example`, `support:*`
  - `core-api` → `admin:admin-api`, `clients:client-ai`, `storage:object-storage` 는 **`runtimeOnly`**
  - `clients:client-ai` → `core:core-domain`: 도메인 포트 구현
  - `storage:object-storage` → `core:core-domain`: 객체 저장 포트 구현
  - `storage:db-core` → `core-enum`
  - `security:security-core` → `core-enum`
- `core-domain`과 `core-enum`은 어떤 프로젝트 모듈에도 의존하지 않는 하위 모듈이다.
- `storage:db-core` 는 JPA starter 를 노출해 core-api 의 Implement 레이어가 Repository 인터페이스를
  직접 주입받는다. 단, **Entity 는 Implement(Mapper) 밖으로 나가지 않는다** ([layers.md](layers.md)).
- 설정은 `spring.config.import` 로 모듈별 yml(`db-core.yml`, `object-storage.yml`, `client-ai.yml`,
  `security-core.yml`, `logging.yml`, `monitoring.yml`, `client-example.yml`)을 합성한다. 프로파일: `local`, `local-dev`, `dev`, `live`
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

- 외부 HTTP·SDK·AI 모델 연동은 `clients:{대상}` 모듈로 분리한다. HTTP 연동은 API 인터페이스 +
  구현 클라이언트 + 전용 요청/응답 DTO로 구성한다.
- 클라이언트의 DTO 는 외부 와이어 포맷 표현이므로 core 도메인 객체와 분리한다.
- 외부 구현이 도메인 행위를 수행할 때는 `core-domain`의 포트를 구현하고, 실행 모듈에
  `runtimeOnly`로 조립한다. core는 특정 제공자나 SDK 타입을 컴파일 시점에 알지 않는다.
- 외부 SDK의 예상 가능한 실패는 포트가 정의한 예외로 변환한다. Service는 그 예외만 복구하고,
  예상하지 않은 `RuntimeException`을 외부 장애로 뭉뚱그려 삼키지 않는다.

## storage:object-storage: 객체 저장소 격벽

- AWS SDK와 S3 버킷 설정을 소유하고 `core-domain`의 `ObjectStorage` 포트를 구현한다.
- `ResumeFile`처럼 업무 의미가 있는 타입과 객체 키 규칙은 core의 해당 개념이 소유한다.
- 외부 I/O이므로 호출은 Service 흐름에 남고 DB 트랜잭션 안에서 호출하지 않는다.
- `core-api`는 이 모듈을 `runtimeOnly`로 조립하므로 S3와 AWS SDK를 컴파일 시점에 알지 않는다.
- AWS SDK 실패는 `ObjectStorageException`으로 변환해 저장 기술의 예외가 core에 침투하지 않게 한다.

## clients:client-ai: AI 모델 격벽

- Spring AI, 모델 제공자와 프롬프트를 소유하고 `core-domain`의 `DocumentSummarizer`를 구현한다.
- 현재 구현은 서울 리전 Bedrock Converse 엔드포인트에서 Sonnet 5 글로벌 추론 프로필에 PDF 바이트를
  문서 입력으로 전달한다. Sonnet 5는 현재 서울 In-Region 추론을 지원하지 않는다.
- `Resume`이나 DB 식별자를 알지 않으며 `PDF 바이트 → 요약문` 포트만 구현한다.
- `core-api`는 이 모듈을 `runtimeOnly`로 조립하므로 특정 AI 구현에 컴파일 의존하지 않는다.
- Spring AI·Bedrock·PDF 읽기의 예상 가능한 실패는 `DocumentSummarizationException`으로 변환한다.

## tests/api-docs: 테스트 전용 공유 모듈

- `RestDocsTest` 베이스 클래스와 restdocs-api-spec 의존성을 담는다. 각 모듈이
  `testImplementation(project(":tests:api-docs"))` 로 가져다 쓴다 ([testing.md](testing.md), [api-docs.md](api-docs.md)).
