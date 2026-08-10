# 모듈 구조

[← 허브로](README.md)

```text
moimyeon/
├── admin/
│   └── admin-api        어드민 API. core-api 런타임에 조립되는 비부트 모듈
├── core/
│   ├── core-batch       배치 실행 모듈 (독립 bootJar)
│   ├── core-enum        도메인 전역 공유 Enum 만 격리 (최하위 모듈)
│   ├── core-worker      백그라운드 작업 실행 모듈 (독립 bootJar, 현재 notification 패키지)
│   └── core-api         API 서버 실행 모듈. 도메인 + api + 영역이 소유하는 외부 연동 계약
├── security/
│   └── security-core    Spring Security 필터 체인·JWT·OAuth (인증 기술 격벽)
├── storage/
│   ├── db-core          JPA Entity / Repository / schema.sql (RDB 접근)
│   ├── object-storage   AWS SDK S3 객체 저장·조회 격벽
│   └── redis-core       Redis 기반 저장·동기화 기술 격벽
├── clients/
│   ├── bedrock-client   Spring AI · Bedrock 외부 모델 클라이언트
│   └── client-example   외부 HTTP 클라이언트 (HTTP Interface)
├── support/
│   ├── logging          로깅 설정(yml)
│   └── monitoring       모니터링 설정(yml)
└── tests/
    └── api-docs         RestDocs 테스트 베이스 (테스트 전용 공유 모듈)
```

## 의존 규칙

- 실행 가능한 산출물(bootJar)은 `core:core-api`, `core:core-batch`, `core:core-worker`이다. `core-api`는 외부 구현이
  계약을 참조할 수 있도록 plain `jar`도 함께 만들고, 나머지는 `jar`만 만든다.
- 컴파일 타임 의존 방향:
  - `core-api` → `core-enum`, `security:security-core`, `storage:db-core`, `clients:client-example`, `support:*`
  - `core-worker` → `core-enum`, `storage:redis-core`, `support:monitoring`, `support:logging`
  - `core-api` → `admin:admin-api`, `clients:bedrock-client`, `storage:object-storage`, `storage:redis-core` 는 **`runtimeOnly`**
  - `clients:bedrock-client`, `storage:object-storage` → `core-api`: 이력서 영역 계약 구현
  - `storage:redis-core` → `core-api`, `core-enum`: 알림 Relay 전달 계약과 이벤트 카탈로그 구현
  - `storage:db-core` → `core-enum`
  - `security:security-core` → `core-enum`
- `storage:db-core` 는 JPA starter 를 노출해 core-api 의 Implement 레이어가 Repository 인터페이스를
  직접 주입받는다. 단, **Entity 는 Implement(Mapper) 밖으로 나가지 않는다** ([layers.md](layers.md)).
- 설정은 `spring.config.import` 로 모듈별 yml(`db-core.yml`, `object-storage.yml`, `redis-core.yml`, `bedrock-client.yml`,
  `client-example.yml`, `security-core.yml`, `logging.yml`, `monitoring.yml`)을 합성한다. 프로파일: `local`, `local-dev`, `dev`, `live`
  (+`test` 는 프로파일 그룹으로 local 상속 — [storage.md](storage.md)).

## core-worker: 백그라운드 작업 조립

- 현재는 `worker.notification` 패키지만 두고 알림 작업을 실행한다.
- 다른 워커가 추가되더라도 처음부터 실행 모듈을 나누지 않는다.
- 작업별 실행 주기·확장 단위 또는 커넥션 풀을 독립적으로 운영해야 할 때 모듈이나 자원 풀을 분리한다.

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
- AI·객체 저장 계약은 이를 사용하는 이력서 영역이 소유하고, 벤더별 구현을 `runtimeOnly`로 조립한다.
  core는 특정 제공자나 SDK 타입을 컴파일 시점에 알지 않는다.
- 외부 SDK의 예상 가능한 실패는 포트가 정의한 예외로 변환한다. Service는 그 예외만 복구하고,
  예상하지 않은 `RuntimeException`을 외부 장애로 뭉뚱그려 삼키지 않는다.

## storage:object-storage: 객체 저장소 격벽

- AWS SDK와 S3 버킷 설정을 소유하고 core-api의 `ResumeFileStore` 계약을 구현한다.
- `ResumeFile`처럼 업무 의미가 있는 타입은 core의 이력서 영역이 소유한다. 객체 키 생성은 저장 구현의 세부사항으로 둔다.
- 외부 I/O이므로 호출은 Service 흐름에 남고 DB 트랜잭션 안에서 호출하지 않는다.
- `object-storage`는 `ResumeFileStore` 계약 구현을 위해 core-api를 compile-time에 의존한다.
- `core-api`에는 `runtimeOnly`로 조립한다.
- AWS SDK 실패는 구현체에서 이력서 영역의 `ResumeFileStorageException`으로 바로 변환한다.

## storage:db-core: Outbox 전달 생명주기

- Outbox는 `PENDING`, `PROCESSING`, 선점 토큰과 lease 만료 시각을 저장한다. 이 값은 알림 도메인 상태가 아니라
  DB Outbox에서 메시지 저장소로 전달되는 작업의 영속 생명주기다.
- `core-api`의 `OutboxClaimManager`가 짧은 트랜잭션에서 `FOR UPDATE SKIP LOCKED`로 배치를 조회하고
  `PROCESSING`으로 선점한다. Redis 발행은 이 트랜잭션이 커밋된 뒤 수행한다.
- 발행 성공 시 현재 선점 토큰이 일치하는 행만 삭제하고, 발행 실패 시 같은 토큰의 행만 `PENDING`으로 되돌린다.
  프로세스가 종료되면 lease가 만료된 뒤 다른 실행이 다시 선점한다.
- `SKIP LOCKED`와 처리 상태는 중복 가능성을 줄이지만 exactly once를 만들지 않는다. 발행 성공 후 완료 기록 전 종료되면
  같은 `outboxId`가 다시 발행될 수 있으며, 이 동작이 현재 단계의 at least once 경계다.

## storage:redis-core: Redis 기반 저장·동기화 기술 격벽

- Spring Data Redis와 Redis 연결 설정을 소유하고 core-api의 `NotificationMessagePublisher` 계약을 구현한다.
- core-api의 `OutboxRelayCoordinator` 계약도 구현해 여러 API 인스턴스 중 한 곳만 미처리 Outbox 재전달 폴링을 시작하게 한다.
  Redis 락은 동일한 DB 스캔을 줄이는 실행 조정 수단이며 Outbox 전달 정확성의 최종 기준으로 사용하지 않는다.
- `local`, `local-dev`, `dev`, `staging`, `live`에서는 Redis 구현체가 조정 계약을 담당하고,
  `test`에서만 core-api의 `DirectOutboxRelayCoordinator`가 재전달을 바로 실행한다. `test` 프로파일이 `local`을 상속하므로
  Redis 구현체는 `local & !test` 조건으로 테스트에서 제외한다. 스케줄러는 환경과 무관하게 계약을 필수로 주입받는다.
- `RelayMessage`를 `eventId`, `eventType`, `payload` 필드로 `notification-events` Stream에 추가한다.
- Redis 명령과 직렬화 형식은 이 모듈 밖으로 노출하지 않는다.
- Redis 저장 실패는 삼키지 않고 호출자에게 전달한다. 생산자 `MessageRelay`가 이 실패를 기준으로 Outbox를 보존한다.
- 재전달 실행 권한은 TTL이 있는 소유자 토큰으로 획득하고, 해제할 때 현재 토큰이 일치하는 락만 삭제한다.
- `core-api`에는 `runtimeOnly`로 조립한다. 실제 어댑터 빈은 `local`, `local-dev`, `dev`, `staging`, `live` 프로파일에서 활성화한다.
- Consumer Group을 만들고 신규 메시지를 Worker별로 분배한다. 처리에 실패한 메시지는 ACK하지 않고 Pending으로 남기며,
  최소 유휴 시간이 지난 Pending 메시지는 다른 Worker가 다시 선점한다.
- Stream 보존 길이와 외부 발송 완료 이후의 채널별 멱등성 정책은 아직 확정 범위가 아니다.

## clients:bedrock-client: AI 모델 격벽

- core-api의 이력서 영역이 `ResumeSummaryGenerator` 계약과 실패 의미를 소유한다.
- `bedrock-client`는 Spring AI, 모델 제공자와 프롬프트를 소유하고 이 계약을 구현한다.
- 현재 구현은 서울 리전 Bedrock Converse 엔드포인트에서 Sonnet 5 글로벌 추론 프로필에 PDF 바이트를
  문서 입력으로 전달한다. Sonnet 5는 현재 서울 In-Region 추론을 지원하지 않는다.
- 글로벌 추론 프로필은 요청을 다른 AWS 상용 리전에서 처리할 수 있으므로 한국 내 처리만을 보장하지 않는다.
  이력서에는 개인정보가 포함될 수 있어 운영 활성화 전에 국외 처리 고지, 적법한 처리 근거, 보존·삭제 기준과
  조직의 개인정보·보안 승인을 확정해야 한다. 이 조건을 충족하지 못하면 글로벌 프로필을 사용하지 않는다.
- `Resume`이나 DB 식별자를 알지 않으며 `PDF 바이트 → 요약문` 계약만 구현한다.
- `core-api`는 이 모듈을 `runtimeOnly`로 조립하므로 특정 AI 구현에 컴파일 의존하지 않는다.
- `bedrock-client`는 `ResumeSummaryGenerator` 계약 구현을 위해 core-api를 compile-time에 의존한다.
- Spring AI·Bedrock·PDF 읽기의 예상 가능한 실패는 `ResumeSummaryGenerationException`으로 바로 변환한다.

## tests/api-docs: 테스트 전용 공유 모듈

- `RestDocsTest` 베이스 클래스와 restdocs-api-spec 의존성을 담는다. 각 모듈이
  `testImplementation(project(":tests:api-docs"))` 로 가져다 쓴다 ([testing.md](testing.md), [api-docs.md](api-docs.md)).
