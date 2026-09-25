# MOI-541 알림 소비 호환 선행 배포

## 목적

- 새 룸 생명주기 알림을 생산하기 전에 worker가 새 `EventType`을 해석하고 전달할 수 있게 한다.
- API 우선 롤링 배포 구간에서 구버전 worker가 새 이벤트를 DLQ 처리하고 ACK하는 알림 유실을 막는다.

## 범위

- `ROOM_CONFIRMED`, `ROOM_COMPLETED`, `ROOM_CANCELED`, `ROOM_REVIEW_REQUESTED` 이벤트 타입 추가
- worker의 룸 생명주기 payload 검증과 채널별 알림 변환 추가
- 이벤트 타입별 단위 테스트 추가

## 제외

- API와 worker에서 새 이벤트를 생산하지 않는다.
- 룸 상태 전이, 출석, 자동 취소 정책은 후속 기능 PR에서 배포한다.

## 검증

- `./gradlew test ktlintCheck`
- `NotificationMessageHandlerTest`
