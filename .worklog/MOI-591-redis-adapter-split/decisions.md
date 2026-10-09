# MOI-591 결정 기록

## D-1: API 쪽 어댑터를 새 모듈로 옮긴다

- 결정: `storage:redis-core`에는 Redis 연결·Stream 이름 설정과 Worker의 소비(Consumer·재시도·DLQ·메트릭)만 남기고,
  core-api·admin-api 계약을 구현하는 발행(`RedisNotificationMessagePublisher`), 재전달 잠금(`RedisOutboxRelayCoordinator`,
  `RedisOutboxRelayProperties`), 운영 조회(`RedisAdminNotificationOperationsReader`)를 `storage:redis-api-adapter`로 옮긴다.
- 이유: 옮길 파일이 적은 쪽이 API 쪽이고, Worker 의존(`storage:redis-core`)과 설정 import(`redis-core.yml`)를 그대로 둘 수 있다.
  redis-core는 core-enum에만 의존하게 되어 core-api 변경이 Worker 빌드 입력에서 빠진다(MOI-590의 영향 판단 단순화).
- 검토한 다른 안: 계약(인터페이스)을 core-enum 같은 하위 모듈로 옮기기. 계약이 core-api 도메인 타입(`OutgoingNotification`)을
  끌고 와 도메인 경계가 흐려진다. Worker 쪽을 새 모듈로 빼기는 Worker 의존·스캔 설정까지 바꿔 변경이 커진다.
- 패키지는 그대로(`io.plady.moimyeon.storage.redis`). Kotlin `internal`은 모듈 단위라 옮긴 클래스끼리만 쓰며 문제 없다.
  core-api는 `io.plady.moimyeon` 전체를 스캔하므로 빈 등록은 그대로다.
- 소비자 통합 테스트(`RedisNotificationStreamConsumerIT`)는 redis-core에 두고 발행기 대신 Stream에 직접 기록한다.
  Worker 쪽 로직만 바꿀 때 core-api를 컴파일하지 않고 검증하려는 것이다(code-reviewer 권장).
  두 모듈 사이 계약(발행 필드, DLQ 필드)은 `redis-api-adapter`의 `RedisNotificationStreamContractIT`가 실제 발행기·소비자·운영 조회로 확인한다.
- 재전달 잠금 설정(`outbox-relay`)은 `redis-core.yml`에 그대로 둔다. Worker가 읽기만 하고 쓰지 않는 값이며, 파일을 나누면
  core-api의 설정 import까지 바뀌어 이득보다 변경이 크다.
- 조립 누락을 잡는 테스트를 양쪽에 둔다(qa-reviewer 권고). Worker는 admin-api·redis-api-adapter 클래스가 없어야 하고,
  core-api는 redis-api-adapter·redis-core 클래스가 있어야 한다. test 프로필은 Redis 빈을 끄므로 컨텍스트 테스트로는 못 잡는다.
- Worker의 `isTransitive = false`는 core-api가 딸려 오는 것을 막으려던 것이라 이유가 사라져 지웠다.
