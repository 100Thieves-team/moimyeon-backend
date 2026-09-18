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
│   ├── bedrock-client   PDF 텍스트 전처리 · Spring AI · Bedrock 외부 모델 클라이언트
│   ├── client-example   외부 HTTP 클라이언트 (HTTP Interface)
│   ├── email-client      SES 기본 발송과 Gmail 폴백 격벽
│   └── web-push-client   Firebase Admin SDK 기반 FCM 웹 푸시 격벽
├── support/
│   ├── logging          로깅 설정(yml)
│   └── monitoring       모니터링 설정(yml)
└── tests/
    └── api-docs         RestDocs 테스트 베이스 (테스트 전용 공유 모듈)
```

## 의존 규칙

- 실행 가능한 산출물(bootJar)은 `core:core-api`, `core:core-batch`, `core:core-worker`이다. `core-api`와
  `core-worker`는 외부 구현이 각 영역의 계약을 참조할 수 있도록 plain `jar`도 함께 만든다.
- 컴파일 타임 의존 방향:
  - `core-api` → `core-enum`, `security:security-core`, `storage:db-core`, `clients:client-example`, `support:*`
  - `core-worker` → `core-enum`, `storage:db-core`, `storage:redis-core`, `support:monitoring`, `support:logging`
  - `core-api` → `admin:admin-api`, `clients:bedrock-client`, `storage:object-storage`, `storage:redis-core` 는 **`runtimeOnly`**
  - `core-worker` → `clients:email-client`, `clients:web-push-client` 는 **`runtimeOnly`**
  - `clients:bedrock-client`, `storage:object-storage` → `core-api`: 이력서 영역 계약 구현
  - `clients:email-client` → `core-worker`, `core-enum`: 이메일 발송 계약과 알림 메시지 구현
  - `clients:web-push-client` → `core-worker`, `core-enum`: 웹 푸시 발송 계약과 알림 메시지 구현
  - `storage:redis-core` → `core-api`, `admin-api`, `core-enum`: 알림 Relay·운영 조회 계약과 이벤트 카탈로그 구현
  - `storage:db-core` → `core-enum`
  - `security:security-core` → `core-enum`
- `storage:db-core` 는 JPA starter 를 노출해 core-api 의 Implement 레이어가 Repository 인터페이스를
  직접 주입받는다. 단, **Entity 는 Implement(Mapper) 밖으로 나가지 않는다** ([layers.md](layers.md)).
- 설정은 `spring.config.import` 로 모듈별 yml(`db-core.yml`, `object-storage.yml`, `redis-core.yml`, `bedrock-client.yml`, `email-client.yml`,
  `web-push-client.yml`, `client-example.yml`, `security-core.yml`, `logging.yml`, `monitoring.yml`)을 합성한다. 프로파일: `local`, `local-dev`, `dev`, `staging`, `live`
  (+`test` 는 프로파일 그룹으로 local 상속 — [storage.md](storage.md)).

## core-worker: 백그라운드 작업 조립

- 현재는 `worker.notification` 패키지만 두고 알림 작업을 실행한다.
- `NotificationMessageHandler` 한 클래스가 `EventType`에 따라 Stream payload와 채널을 검증하고 수신자·제목·본문·이동 경로를
  담은 채널별 `Notification`으로 변환한다. 이벤트별 Handler 인터페이스와 구현체는 두지 않는다.
- 이벤트별 발송 경로는 `core-enum`의 `EventType.notificationChannels`가 소유한다. 새 이벤트를 추가할 때 이벤트 종류와
  채널 정책, 단일 Handler의 payload 변환 분기만 추가하며 사건마다 웹 푸시·이메일 포트를 다시 만들지 않는다.
- 웹 푸시와 이메일은 서로의 fallback이 아니다. EventType의 채널 집합은 이메일만, 웹 푸시만 또는 둘 다 발송하는 제품 정책이다.
  `redis-core`가 이 정책을 채널별 Stream 메시지로 확장하므로 각 채널은 독립적으로 ACK·재처리된다.
- `ChannelNotificationSender`는 한 `Notification`에 지정된 한 채널만 처리한다. 웹 푸시 실패는 웹 푸시 메시지만 Pending에
  남기며, 별도 이메일 메시지의 처리 결과에는 영향을 주지 않는다.
- `MemberNotificationRecipientFinder`는 `MemberRepository.findByIdAndDeletedAtIsNull`로 살아있는 회원의 이메일을 읽어
  `NotificationRecipient`로 변환하고, `WebPushSubscriptionRepository`에서 같은 회원의 웹 푸시 등록을 함께 합성한다.
  `MemberEntity`와 `WebPushSubscriptionEntity`는 Finder 밖으로 노출하지 않는다.
- 살아있는 회원을 찾지 못하면 `NotificationRecipientNotFoundException`을 영구 실패로 분류해 DLQ에 격리한다.
- 외부 발송 성공과 ACK 사이에는 중복 가능성이 남는다. FCM과 SES에는 `eventId`를 요청 멱등 키로 사용하는 계약이 없고,
  별도 완료 원장도 구축하지 않는다. 현재 목표는 exactly once가 아니라 at least once이며, 원장 테이블·선점·lease·정리 배치와
  발송마다 발생하는 DB 쓰기 복잡도를 고려해 잔여 중복 가능성과 타협한다. Redis 실행 권한, Outbox의 `SKIP LOCKED` 선점,
  채널별 Stream 메시지 분리까지로 동시 중복을 최소화하고, 외부 발송 성공 뒤 ACK 전 실패 구간은 남긴다.
- payload가 잘못됐으면 영구 실패로, 일시적인 외부 발송 실패는 재시도 가능 실패로 Redis Consumer에 전달한다.
- `NotificationWorkerConfiguration`은 Redis 소비가 활성화된 프로파일에서 단일 Handler, 채널 발송 조립체와 Worker를 함께 등록한다.
  Handler를 nullable로 두지 않으므로 `EmailSender`, `WebPushSender` 또는 Consumer 구현이 빠지면 애플리케이션 시작이 실패한다.
- 기본 `local`에서는 외부 벤더 자격 증명 없이 개발할 수 있도록 Stream 소비를 끈다. 실제 발송 Bean이 활성화되는 `local-dev`, `dev`,
  `staging`, `live`에서는 소비도 함께 활성화한다.
- AWS에서는 ALB가 없는 독립 ECS Service로 배포한다. Worker 전용 Security Group과 IAM Role을 사용하고,
  SES 전송 권한은 API Task Role에 주지 않는다. 외부 비밀값이 SSM에 준비되기 전에는 desired count를 0으로 유지한다.
- DB 스키마 마이그레이션은 `core-api`가 소유하며 `core-worker`에서는 Flyway를 비활성화한다. 두 실행 단위가
  배포 중 동시에 같은 마이그레이션 경계를 경쟁하지 않게 하기 위한 선택이다.
- 다른 워커가 추가되더라도 처음부터 실행 모듈을 나누지 않는다.
- 작업별 실행 주기·확장 단위 또는 커넥션 풀을 독립적으로 운영해야 할 때 모듈이나 자원 풀을 분리한다.

## admin-api: 런타임 조립

어드민은 **별도 배포 없이 core-api 프로세스에 함께 뜨지만, core 와 컴파일 타임에 서로를 모른다.**

- `core-api` 가 `runtimeOnly(project(":admin:admin-api"))` 로만 의존한다.
  admin 컨트롤러·서비스는 컴포넌트 스캔으로 발견될 뿐, core 코드에서 admin 타입을 참조할 수 없다(역방향도 동일).
- admin 의 모든 클래스는 `Admin` 접두사를 붙인다(`AdminExampleService`, `AdminControllerAdvice`).
  같은 프로세스에 조립되므로 빈 이름·클래스명 충돌을 접두사로 예방한다.
- 알림 운영 화면은 Thymeleaf SSR로 렌더링한다. Page Controller가 같은 프로세스의 `AdminNotificationOperationsReader`를 직접
  호출하며 자기 REST API를 다시 HTTP로 호출하지 않는다. JSON 소비자가 생기면 같은 Reader 위에 별도 Controller를 추가한다.
- Redis 구현은 Pending 요약과 최신 DLQ 50건을 읽기 전용으로 제공한다. 재전달·삭제는 감사와 중복 정책이 확정될 때까지 제외한다.
- 회원과 관리자는 같은 `member` 테이블을 사용하고 `MemberRole`로 권한을 구분한다.
  `/admin/**`는 JWT의 `ROLE_ADMIN`만 접근하며, 대시보드와 Redis Reader는 전 실행 프로파일에서 사용하되 `test`에서만 제외한다.
- 초기 화면은 Bootstrap 5.3 CSS CDN을 사용한다. 외부 CDN 접근이 제한되는 운영망 요구가 생기면 정적 자산을 애플리케이션에
  포함하는 방식으로 교체한다.
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

## clients:email-client: 이메일 제공자 격벽

- `core-worker`가 소유한 `EmailSender` 계약을 구현하고 AWS SDK·SMTP 타입을 Worker 밖에 둔다. SES와 Gmail은 하나의
  이메일 가용성 정책으로 함께 움직이므로 벤더별 모듈 대신 같은 채널 모듈 안의 구현으로 둔다.
- 이메일은 SES를 기본 제공자로 사용하고, SES가 네트워크 단절·429·5xx로 사용할 수 없을 때만 Gmail SMTP로 fallback한다.
- SES의 400 계열 요청 거절은 다른 제공자에 같은 잘못된 메시지를 반복하지 않고 영구 실패로 전파한다.
- SES와 Gmail이 모두 실패하면 Gmail 실패를 전파하되 SES 실패도 suppressed cause로 보존한다. Worker는 이 실패를 기준으로
  Stream 메시지를 ACK하지 않는다.
- `local-dev`, `dev`, `staging`, `live`에서만 실제 발송 Bean을 등록한다. `core-worker`는 이 모듈을 `runtimeOnly`로 조립하므로
  AWS SDK와 메일 라이브러리를 컴파일 시점에 알지 않는다.

## clients:web-push-client: FCM 웹 푸시 격벽

- `core-worker`가 소유한 `WebPushSender`를 구현하고 Firebase Admin SDK 타입을 Worker 밖에 둔다.
- 회원별 웹 푸시 등록을 최대 500개씩 나눠 Firebase Installation ID 멀티캐스트로 보낸다. 제목·본문, 클릭 이동 주소,
  `eventId`와 `eventType`을 FCM 요청으로 변환한다.
- 개별 응답의 `UNREGISTERED` 등록은 `InvalidWebPushRegistrationRemover`를 통해 물리 삭제한다. 클라이언트 모듈은 JPA Repository를
  직접 알지 않으며 DB 구현이 저장 해시와 원문을 함께 확인한다.
- `INTERNAL`, `QUOTA_EXCEEDED`, `UNAVAILABLE`은 재시도 가능 오류로, 요청·인증 계열 오류는 영구 오류로 변환한다.
- 기본값은 Application Default Credentials다. AWS ECS에서는 SSM SecureString의 서비스 계정 JSON을
  `FIREBASE_SERVICE_ACCOUNT_JSON`으로 주입할 수 있으며, 두 방식 모두 키를 소스나 yml에 저장하지 않는다.
- `local-dev`, `dev`, `staging`, `live`에서만 실제 발송 Bean을 등록한다. `core-worker`는 이 모듈을 `runtimeOnly`로 조립한다.

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
  같은 `eventId`가 다시 발행될 수 있으며, 이 동작이 현재 단계의 at least once 경계다.

## storage:db-core: 웹 푸시 등록 생명주기

- `web_push_subscription`은 회원별 브라우저 등록 식별자와 마지막 동기화 시각을 저장한다.
- 같은 등록 식별자는 한 회원에게만 연결한다. 다른 회원이 같은 브라우저에서 등록하면 현재 로그인 회원에게 소유권을 이전해
  이전 계정의 알림이 같은 브라우저로 전달되지 않게 한다.
- 등록 식별자는 길이가 길 수 있으므로 원문은 `TEXT`에 보존하고 SHA-256 해시에 유니크 제약을 둔다. 회원별 발송 대상 조회를
  위해 `member_id` 인덱스를 둔다.
- 클라이언트의 반복 등록은 `registration_hash` 유니크 키에 대한 MySQL 원자적 upsert로 한 행을 유지하면서 `registered_at`을
  갱신한다. 애플리케이션은 조회 후 INSERT로 경쟁 구간을 만들지 않고 하나의 upsert 문장만 실행한다.
- 해지는 복원할 업무 삭제가 아니라 현재 외부 전달 경로의 제거이므로 소프트 삭제 없이 물리 삭제한다.
- 등록의 stale 판정 기간과 정리 배치는 제품 사용 패턴을 측정한 뒤 결정한다. FCM이 만료·미등록 응답을 반환할 때의 즉시 삭제는
  FCM 어댑터 슬라이스에서 연결한다.

## storage:redis-core: Redis 기반 저장·동기화 기술 격벽

- Spring Data Redis와 Redis 연결 설정을 소유하고 core-api의 `NotificationMessagePublisher` 계약을 구현한다.
- core-api의 `OutboxRelayCoordinator` 계약도 구현해 여러 API 인스턴스 중 한 곳만 미처리 Outbox 재전달 폴링을 시작하게 한다.
  Redis 락은 동일한 DB 스캔을 줄이는 실행 조정 수단이며 Outbox 전달 정확성의 최종 기준으로 사용하지 않는다.
- `local`, `local-dev`, `dev`, `staging`, `live`에서는 Redis 구현체가 조정 계약을 담당하고,
  `test`에서만 core-api의 `DirectOutboxRelayCoordinator`가 재전달을 바로 실행한다. `test` 프로파일이 `local`을 상속하므로
  Redis 구현체는 `local & !test` 조건으로 테스트에서 제외한다. 스케줄러는 환경과 무관하게 계약을 필수로 주입받는다.
- `RelayMessage`의 `EventType.notificationChannels`를 채널별 메시지로 확장해 `eventId`, `eventType`, `channel`, `payload`
  필드로 `notification-events` Stream에 추가한다. 여러 채널의 `XADD`는 한 Lua 스크립트에서 실행해 일부 채널만
  저장되는 상태를 만들지 않는다.
- Redis 명령과 직렬화 형식은 이 모듈 밖으로 노출하지 않는다.
- Redis 저장 실패는 삼키지 않고 호출자에게 전달한다. 생산자 `NotificationRelay`가 이 실패를 기준으로 Outbox를 보존한다.
- 모든 채널 메시지 저장 후 Outbox 완료 기록 전에 프로세스가 종료되면 같은 채널 메시지들이 다시 발행될 수 있다.
  유실 방지를 우선해 이 잔여 중복 가능성과 타협한다.
- 재전달 실행 권한은 TTL이 있는 소유자 토큰으로 획득하고, 해제할 때 현재 토큰이 일치하는 락만 삭제한다.
- `core-api`에는 `runtimeOnly`로 조립한다. 실제 어댑터 빈은 `local`, `local-dev`, `dev`, `staging`, `live` 프로파일에서 활성화한다.
- Consumer Group을 만들고 신규 채널 메시지를 Worker별로 분배한다. 성공 메시지만 ACK하고 재시도 가능 실패는 Pending에 남긴다.
- Pending delivery count와 마지막 전달 후 경과 시간을 사용해 지수 backoff를 적용한다. 기본값은 최초 1분, 최대 15분,
  전체 처리 시도 5회다. 후보 100개 중 처리 가능한 메시지를 배치당 10개까지 `XCLAIM`한다.
- 영구 실패와 재시도 상한 초과 메시지는 `notification-dead-letters` Stream에 원본·실패 문맥을 보존한다.
- DLQ `XADD`와 원본 `XACK`은 하나의 Lua 스크립트에서 순서대로 실행한다. DLQ 기록이 실패하면 ACK가 실행되지 않아 원본은
  Pending에 남는다.
- Redis의 최종 처리 결과를 `notification.worker.messages` Counter로 기록한다. `success`는 ACK 성공 후,
  `dead_letter`는 DLQ 기록과 원본 ACK 성공 후에만 증가하고, Pending에 남은 처리 시도는 `retry`로 기록한다.
- Counter의 `event_type`, `channel`, `outcome` 라벨은 유한한 Enum·결과 값만 사용한다. 손상된 Stream의 임의 값은
  `UNKNOWN`으로 합쳐 Prometheus 라벨 카디널리티 증가를 막는다.
- `notification.worker.pending.messages` Gauge는 한 소비 주기의 마지막에 Consumer Group의 `XPENDING` 요약값으로
  갱신한다. 여러 Worker 인스턴스가 같은 Group 전체 값을 노출하므로 Grafana에서는 합계가 아닌 `max by (consumer_group)`로 조회한다.
- admin-api의 `AdminNotificationOperationsReader`를 구현해 Consumer Group Pending 수, DLQ 전체 건수와 최신 메시지를 조회한다.
  Redis Stream 필드는 Admin 전용 읽기 모델로 변환하며 Admin Controller에 Redis 타입을 노출하지 않는다.
- DLQ re-drive, 보존 길이와 운영 알람 임계치는 아직 확정 범위가 아니다.

## support:monitoring: 운영 관측 격벽

- Actuator와 Prometheus Registry를 조립하지만 공개 포트에는 ALB 상태 확인용 `/actuator/health`만 노출한다.
  `/actuator/prometheus`는 별도의 보호된 management 포트를 구성하기 전까지 노출하거나 수집하지 않는다.
- 개별 메트릭의 이름·라벨·증가 시점은 처리 결과를 소유한 `redis-core`에 두고, `support:monitoring`은
  알림 업무를 모르는 공통 Exporter 설정으로 유지한다.
- Admin SSR은 Redis의 현재 상태와 DLQ 원문을 조회한다. 보호된 수집 경로를 마련하면 Prometheus·Grafana가
  Worker 처리량과 Pending의 시간에 따른 변화를 담당한다.
- 알람 임계치와 Grafana Dashboard JSON은 실제 처리량·재시도율 기준선을 측정한 뒤 확정한다.

## clients:bedrock-client: AI 모델 격벽

- core-api의 이력서 영역이 `ResumeSummaryGenerator` 계약과 실패 의미를 소유한다.
- `bedrock-client`는 Spring AI, 모델 제공자와 프롬프트를 소유하고 이 계약을 구현한다.
- 현재 구현은 PDFBox로 이력서 텍스트를 추출해 NFC로 정규화하고 명백히 깨진 문자를 거부한 뒤,
  이메일·한국 유·무선 전화번호·주민등록번호, 대표 도로명 주소와 라벨이 있는 학번을 입출력에서 마스킹한다.
  OCR이나 PDF 원본 fallback은 사용하지 않는다.
- 검증·마스킹한 텍스트만 서울 리전 Bedrock Converse 엔드포인트의 Claude 3.5 Sonnet In-Region direct model로 전달한다.
- 응답은 한국어 1~2문장이고 평가 표현이 없어야 한다. 규칙을 어기면 최대 한 번 다시 생성하고 재차 위반하면
  `ResumeSummaryGenerationException`으로 처리한다.
- PDF는 최대 50페이지·추출 문자열 60,000자로 제한한다. 서비스 시작 시 만든 45초 monotonic deadline을
  S3 저장·조회와 생성기가 공유한다. S3 요청은 설정 상한과 남은 시간 중 짧은 timeout을 사용하고, PDFBox는
  content stream 연산 중 deadline을 확인한다. 회당 20초 모델 호출은 마칠 시간이 남아 있을 때만 시작한다.
- 표·다단 레이아웃의 줄·순서 변화는 모델이 문맥으로 해석하도록 허용한다. 텍스트가 없거나 이미지 전용·암호화·손상 PDF는
  `ResumeSummaryGenerationException`으로 거부한다.
- `Resume`이나 DB 식별자를 알지 않으며 `PDF 바이트 → 요약문` 계약만 구현한다.
- `core-api`는 이 모듈을 `runtimeOnly`로 조립하므로 특정 AI 구현에 컴파일 의존하지 않는다.
- `bedrock-client`는 `ResumeSummaryGenerator` 계약 구현을 위해 core-api를 compile-time에 의존한다.
- Spring AI·Bedrock·PDF 읽기의 예상 가능한 실패는 `ResumeSummaryGenerationException`으로 바로 변환한다.

## tests/api-docs: 테스트 전용 공유 모듈

- `RestDocsTest` 베이스 클래스와 restdocs-api-spec 의존성을 담는다. 각 모듈이
  `testImplementation(project(":tests:api-docs"))` 로 가져다 쓴다 ([testing.md](testing.md), [api-docs.md](api-docs.md)).
