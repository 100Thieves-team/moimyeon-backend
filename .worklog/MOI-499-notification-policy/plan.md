# MOI-499 구현 계획

## 단계

- [x] 1. 브랜치 준비 (`feat/MOI-499-notification-policy`, 사용자 승인 2026-09-24)
- [x] 2. 컨텍스트 수집 → [context.md](context.md)
- [x] 3. 구현 계획 (이 문서) — **체크포인트 A: 계획 승인** (2026-09-24, worker 메시지 형식은 B안 "보내는 쪽이 완성된 알림을 보냄"으로 확정)
- [x] 4. service 테스트 스켈레톤 — **체크포인트 B: 스펙 승인** (2026-09-24, 리뷰 알림은 공개 시점 발송 (a)안, 메일 링크는 worker가 절대 URL로 변환)
  - 스켈레톤 작성 (2026-09-24): 본문은 `TODO("스켈레톤")`. 컴파일·ktlint 통과, 실행하면 실패(RED)
- [ ] 5. TDD 구현
  - 구현 완료 (2026-09-24). 스켈레톤에서 바뀐 테스트 위치: 완료 알림은 `ClosingSubmissionManagerTest`, 댓글 알림은
    `RoomCommentManagerTest`(단위), 확정·취소·위임 알림은 기존 `RoomConfirmationIT`·`RoomCancellationIT`·`RoomLeaveIT`,
    반려 알림은 `RoomApplicationManagerIT`, 리뷰 공개 알림은 `ReviewPublicationRecorderIT`로 옮겼다(픽스처 재사용).
  - `./gradlew test ktlintCheck` 통과 (2026-09-24): 1,286개, 건너뜀 0, 실패 0. MySQL Testcontainers 스키마 검증(V28 포함) 포함.
- [ ] 6. 리뷰 위임 (code-reviewer, qa-reviewer, 스키마·쿼리 변경 시 db-reviewer)
  - db-reviewer (2026-09-24): 필수 0, 권장 4 모두 반영 — 조건부 UPDATE에 삭제·공개 조건 추가, 마이그레이션 3개로 분리,
    일괄 종료된 신청자를 닫은 뒤 status·handledAt으로 조회(철회 경합 제거), 인덱스에 deleted_at 추가.
  - code-reviewer (2026-09-24): 필수 2 반영 — modules.md를 정책 기반으로 갱신, web-push-client 로그를 kotlin-logging으로.
    권장 5 반영 — notifiedAt JPA 쓰기 제외, 쓸모없던 스케줄 토글 제거, 리뷰 IT를 룸 기준으로 거름, 실패 테스트에 errorType 단언,
    받는 사람 명단을 `ParticipationFinder.getJoinedParticipants`로 통일. 참고 반영 — `isNewHost`, 정책 매핑 단언을 `EventTypeTest`로,
    worker payload에 모르는 필드 무시 명시.
  - 추가 확인 (2026-09-24): 일괄 종료 시각 되짚기가 나노초 시계에서도 MySQL에서 맞는지 `MySqlSchemaValidationIT`에 테스트 추가, 통과.
  - 2차 리뷰 (2026-09-24, 구조 변경 뒤): code-reviewer 필수 1(알림 변환에 남아 있던 else 분기) 반영, 권장 5 반영(읽기 전용 트랜잭션 발행 거부,
    payload 변경 규칙 문서화·모르는 필드 무시 명시, 룸 제목은 RoomFinder 로, 탈퇴 전용 도구, worklog 정리). db-reviewer 필수 0,
    권장 1·2 반영(탈퇴 시 회원 행 잠금, 읽지 못한 outbox 행 보존), 권장 3(방장 위임 경합)은 이번 PR 이전 문제라 tbd 로 남김.
- [ ] 7. 검증 — `./gradlew test ktlintCheck` 통과 (2026-09-24, 2차 리뷰 반영 후): 1,314개, 건너뜀 0, 실패 0
- [ ] 7-C. **체크포인트 C: 구현 승인**
- [ ] 8. 커밋·PR (ship-pr)

## 접근 방식

> **구조 변경 (2026-09-24, 사용자 결정):** 아래 1·3·4절의 "이벤트 = 받는 사람별 알림 요청" 구조는 도메인 사실을 outbox 에 저장하고 소비자가 처리하는 구조로 바뀌었다.
> 도메인 코드는 `OutboxEventPublisher.publish(EventType, payload)`로 **사실**을 발행하고 outbox에는 `OutboxEvent`가 저장된다.
> 커밋 뒤 `OutboxRelay`가 소비자(`OutboxEventConsumer`)에게 넘기고, 알림 소비자(`NotificationEventConsumer`)가
> `NotificationComposer`로 받는 사람별 알림(공통 형식)을 만들어 Redis에 넣는다. 전송 정책은 `EventType`이 아니라 알림 한 건에 붙는다.
> worker는 그대로다. 자세한 이유는 decisions.md.

### 1. 정책을 이벤트 종류에 붙인다

`core-enum`에 `NotificationPolicy`(PUSH_ONLY, PUSH_ELSE_EMAIL, EMAIL_ONLY, PUSH_AND_EMAIL)를 추가한다.
`EventType`은 지금의 채널 목록 대신 정책을 갖고, 채널 목록은 정책에서 계산한다.

| 정책 | Redis에 넣는 메시지 |
| --- | --- |
| PUSH_ONLY | WEB_PUSH 1건 |
| EMAIL_ONLY | EMAIL 1건 |
| PUSH_AND_EMAIL | WEB_PUSH 1건 + EMAIL 1건 (지금과 같음, 채널별로 따로 재시도) |
| PUSH_ELSE_EMAIL | WEB_PUSH 1건. worker가 푸시 결과를 보고 필요하면 같은 처리 안에서 메일을 보낸다 |

Redis 메시지 형태(eventId, eventType, channel, payload)는 그대로 둔다. 이미 쌓여 있는
`ROOM_APPLICATION_ACCEPTED` 메시지도 그대로 처리된다.

### 2. 푸시 결과를 "전달됨 / 전달 안 됨"으로 돌려준다

지금은 `WebPushSender.send`가 결과 없이 예외만 던진다. 이것을 전달 여부를 돌려주도록 바꾼다.

`FcmWebPushSender` 판정 (기기별 결과를 모아서):
1. 한 기기라도 성공 → 전달됨. 다른 기기에서 일시 오류가 있어도 재시도하지 않는다
   (재시도하면 이미 받은 기기에 중복으로 간다).
2. 성공이 없고 일시 오류(`RETRYABLE_FAILURE`)가 하나라도 있음 → 지금처럼 재시도 예외.
3. 성공이 없고 나머지가 모두 `UNREGISTERED` 또는 영구 오류 → 전달 안 됨.
   만료 토큰 삭제는 지금처럼 한다.
4. 등록된 토큰이 없음 → 보내지 않고 전달 안 됨.

`ChannelNotificationSender`:
- WEB_PUSH 메시지: 푸시를 보내고, 전달 안 됨이면서 정책이 PUSH_ELSE_EMAIL이면 메일을 보낸다.
- EMAIL 메시지: 지금과 같다.

바뀌는 동작: 지금은 영구 오류(예: `INVALID_ARGUMENT`)가 나면 메시지를 실패 보관함(dead letter)으로 보낸다.
바뀐 뒤에는 "전달 안 됨"으로 끝나고, PUSH_ELSE_EMAIL이면 메일로 넘어간다. 영구 오류는 경고 로그로 남긴다.

### 3. worker는 알림 내용만 받는다 (보내는 쪽이 공통 형식으로 만든다)

지금은 worker가 이벤트 종류마다 payload의 업무 필드(`applicantMemberId`, `roomId` 등)를 직접 해석해
문구를 만든다. 이벤트가 늘 때마다 worker를 고쳐야 하고, worker보다 API가 먼저 배포되면
새 이벤트를 해석하지 못해 알림이 유실된다.

바꾼 뒤에는 API 쪽이 알림을 **모든 이벤트가 공통으로 쓰는 형식**으로 만들어 outbox에 저장한다.

| 필드 | 뜻 |
| --- | --- |
| `eventId` | 알림 한 건의 식별자 (중복 처리 방지·로그용) |
| `eventType` | 이벤트 종류. worker는 로그·메트릭·푸시 data에 문자열로 싣기만 하고 분기하지 않는다 |
| `policy` | 네 가지 전송 정책 중 하나 |
| `recipientMemberId` | 받는 회원 |
| `title`, `body`, `actionPath` | 알림 제목·본문·눌렀을 때 이동할 경로 |

- 문구는 API 쪽 이벤트가 만든다. worker의 `NotificationMessageHandler`는 이벤트별 분기를 없애고
  공통 형식만 해석한다.
- worker는 `eventType`을 enum으로 해석하지 않는다. 그래서 이 변경 이후에는 이벤트를 추가해도
  worker를 바꿀 필요가 없다.
- 받는 사람의 메일 주소와 기기 토큰은 지금처럼 worker가 발송 시점에 DB에서 조회한다(구독 해제·탈퇴를 반영하기 위해).

### 4. 받는 사람마다 이벤트를 하나씩 발행한다

여러 명에게 가는 알림은 도메인 코드가 받는 사람 수만큼 이벤트를 발행한다. 사람마다 outbox 행과
Redis 메시지가 따로 생기므로, 한 사람에게 보내다 실패해도 다른 사람에게 다시 보내지 않는다.

### 5. 이벤트별 받는 사람

원칙: **이벤트를 일으킨 본인에게는 보내지 않는다.** 참여자는 참여 상태가 `JOINED`인 회원이다.

| 이벤트 | EventType | 받는 사람 | 정책 |
| --- | --- | --- | --- |
| 참여 신청 | `ROOM_APPLICATION_SUBMITTED` | 현재 방장 | PUSH_ELSE_EMAIL |
| 수락 | `ROOM_APPLICATION_ACCEPTED` | 신청자 | PUSH_AND_EMAIL |
| 반려 | `ROOM_APPLICATION_REJECTED` | 신청자 | PUSH_ELSE_EMAIL |
| 확정 | `ROOM_CONFIRMED` | 방장을 뺀 참여자 | PUSH_AND_EMAIL |
| 확정으로 신청 종료 | `ROOM_APPLICATION_CLOSED_BY_CONFIRMATION` | 확정 때 신청이 일괄 종료된 대기 신청자 | PUSH_ELSE_EMAIL (반려와 같은 성격, 2026-09-24 승인) |
| 완료(리뷰 요청) | `ROOM_COMPLETED` | **출석한** 참여자 (결석자 제외, 2026-09-24 사용자 결정) | PUSH_ELSE_EMAIL |
| 취소 | `ROOM_CANCELED` | 취소로 신청이 종료된 대기 신청자 | PUSH_AND_EMAIL |
| 방장 위임 | `ROOM_HOST_DELEGATED` | 새 방장 포함 남은 참여자 전원 (나간 방장 제외) | PUSH_ELSE_EMAIL |
| 리뷰 공개 | `REVIEW_PUBLISHED` | 리뷰 대상자. **작성 시점이 아니라 공개 시각(`visibleAt`)이 지난 뒤** 보낸다. 삭제된 리뷰·자기 자신 리뷰는 보내지 않음. 익명이면 작성자 이름 없음 | PUSH_ONLY |
| 댓글 작성 | `ROOM_COMMENT_POSTED` | 작성자를 뺀 참여자 | PUSH_ONLY |

- 반려 사유는 알림에 넣지 않는다. 알림을 눌러 들어간 화면에서 조회한다(2026-09-24 사용자 결정).
  신청자가 자기 반려 사유를 조회하는 기능은 별도 이슈 MOI-542에서 만든다.
- 완료 알림은 클로징 전원 제출 경로(core-api)에서만 붙인다. 8시간 자동 완료는 다른 팀원이
  core-api로 옮기면서 같은 이벤트를 발행한다.

### 5-1. 리뷰 공개 알림 (2026-09-24 승인)

리뷰는 작성 3시간 뒤 공개되고, 공개 전에만 삭제·수정할 수 있다. core-api 스케줄러가 1분마다
"공개 시각이 지났고, 삭제되지 않았고, 아직 판정하지 않은 리뷰"를 찾아 알림 이벤트를 발행하고
`review.notified_at`(알림 판정을 끝낸 시각)을 기록한다. 서버가 여러 대여도 `notified_at is null`인 행만 채우는
조건부 UPDATE가 1을 돌려준 쪽만 발행하므로 분산 락을 쓰지 않는다(decisions.md). 조건부 UPDATE도 삭제·공개 조건을
다시 걸어, 후보 조회 뒤에 지워진 리뷰는 알리지 않는다.

스키마 변경(마이그레이션 + schema.sql): V29 컬럼 추가, V30 기존 행 백필(모두 판정 끝으로 채움), V31 인덱스
`(notified_at, deleted_at, visible_at)`. MySQL DDL은 암묵 커밋이라 한 파일에서 뒤 문장이 실패하면 재실행이 막혀 파일을 나눴다.

### 5-2. 알림 링크 (2026-09-24 승인)

공통 형식에는 프론트 상대 경로(`/rooms/{roomId}`)만 둔다. worker가 발송 직전에
`notification.action-base-url`(환경별 프론트 주소, worker application.yml에 프로필별 기본값. 사용자 요청 2026-09-24:
하드코딩하지 말고 환경별로 분리)을 앞에 붙여
절대 URL을 만들고, 푸시와 메일에 똑같이 넘긴다. 메일 본문 마지막 줄에 절대 URL을 넣는다.
환경 변수 이름 변경은 별도 infra-change로 한다.

### 6. 알림 문구 (초안)

이벤트 클래스가 제목·본문·이동 경로를 만든다. 사용자가 문구를 위임했다(2026-09-24).
이동 경로는 모두 `/rooms/{roomId}`. 메일도 같은 제목·본문을 쓴다.

| 이벤트 | 제목 | 본문 |
| --- | --- | --- |
| 참여 신청 | 새 참가 신청이 왔어요 | '{방 제목}'에 참가 신청이 들어왔어요. 신청 내용을 확인해 주세요. |
| 수락 | 참가 신청이 수락되었어요 | '{방 제목}' 모임에 참여할 수 있게 되었어요. |
| 반려 | 참가 신청 결과를 알려드려요 | '{방 제목}' 참가 신청이 수락되지 않았어요. |
| 확정 | 모임이 확정되었어요 | '{방 제목}' 모임이 확정되었어요. 일정을 다시 확인해 주세요. |
| 확정으로 신청 종료 | 참가 신청이 마감되었어요 | '{방 제목}' 모임이 확정되어 참가 신청이 마감되었어요. |
| 완료(리뷰 요청) | 모임은 어떠셨나요? | '{방 제목}' 모임이 끝났어요. 함께한 분들에게 후기를 남겨 주세요. |
| 취소 | 모임이 취소되었어요 | '{방 제목}' 모임이 취소되어 참가 신청이 종료되었어요. |
| 방장 위임 (새 방장) | 방장이 되었어요 | '{방 제목}' 모임의 방장을 이어받았어요. |
| 방장 위임 (나머지) | 방장이 바뀌었어요 | '{방 제목}' 모임의 방장이 바뀌었어요. |
| 리뷰 작성 | 새 후기가 도착했어요 | '{방 제목}' 모임에서 받은 후기가 있어요. |
| 댓글 작성 | 새 댓글이 달렸어요 | '{방 제목}'에 새 댓글이 달렸어요. |

## 변경 지점

| 영역 | 파일·패키지 |
| --- | --- |
| 정책 | `core-enum`: `NotificationPolicy` 추가, `EventType` 변경 |
| Redis 발행 | `storage/redis-core/RedisNotificationMessagePublisher` (채널 목록 계산 위치만) |
| 도메인 사실 발행 | `core-api/event`: `OutboxEventPublisher`, `OutboxEvent`, payload, `OutboxRelay` |
| 알림 변환 | `core-api/notification`: `NotificationComposer`, `NotificationOutboxEventConsumer` |
| 이벤트 | `core-api/domain/{room,roomapplication,trust,roomcomment,closing}`에 이벤트 클래스와 발행 코드 |
| 받는 사람 조회 | 참여자 목록·대기 신청자 목록 조회 (필요하면 Repository 조회 메서드 추가, 스키마 변경 없음) |
| worker 발송 | `NotificationMessageHandler`(공통 형식만 해석), `ChannelNotificationSender`, `WebPushSender` 반환형 |
| Redis 메시지 | `NotificationStreamMessage.eventType`을 문자열로, `policy` 필드 추가 |
| FCM | `clients/web-push-client/FcmWebPushSender` 판정 변경 |

스키마 변경은 `review.notified_at` 하나다(5-1). `outbox.event_type`은 `VARCHAR(100)`이라 enum 값만 추가하면 된다.

## 만들 테스트

단위 (MockK):
- `EventType`: 정책별 채널 목록
- `FcmWebPushSender`: 일부 성공 + 일시 오류 → 전달됨·재시도 없음 / 전부 일시 오류 → 재시도 예외 /
  전부 `UNREGISTERED` → 전달 안 됨·토큰 삭제 / 영구 오류만 → 전달 안 됨
- `ChannelNotificationSender`: PUSH_ELSE_EMAIL에서 푸시 전달 안 됨 → 메일 / 전달됨 → 메일 없음 /
  토큰 없음 → 메일 / PUSH_ONLY에서 전달 안 됨 → 메일 없음
- `NotificationMessageHandler`: 공통 형식 해석, 모르는 `eventType` 문자열도 처리, 형식이 깨진 payload는 영구 실패

통합 (`ContextTest`, 이벤트 발행 → outbox 저장 확인):
- 참여 신청 → 방장에게 1건
- 반려 → 신청자에게 1건
- 확정 → 방장 뺀 참여자 수만큼 + 일괄 종료된 대기 신청자 수만큼
- 방장 직접 취소 / 방장 나가기 후 취소 → 대기 신청자 수만큼, 참여자 없음
- 방장 나가기 후 참여자에게 위임 / 대기 신청자에게 위임 → 남은 참여자 전원
- 클로징 전원 제출로 완료 → 출석한 참여자만
- 리뷰 작성 → 대상자 1건, 자기 리뷰면 0건
- 댓글 작성 → 작성자 뺀 참여자 수만큼
- 롤백 시 outbox 행이 남지 않음 (기존 `RoomApplicationNotificationRollbackIT` 방식)

## API 문서 영향

없다. REST API 계약이 바뀌지 않는다.

## 영향받는 외부 소비자

- **프론트엔드 service worker**: 푸시 data의 `eventType`에 새 값 9종이 들어간다. 프론트가 `eventType`으로
  분기한다면 새 값을 알아야 한다. 이동 링크는 `fcmOptions.link`로 가므로 분기가 없으면 영향 없다. PR에서 공유한다.
- **메일 수신자**: PUSH_ELSE_EMAIL 이벤트에서 푸시 미구독 회원이 메일을 받기 시작한다.

## 배포 시 주의

이번 PR이 worker의 메시지 해석 방식 자체를 바꾸므로, **이번 배포 한 번은** API가 먼저 배포되고
worker가 나중에 배포되는 몇 분 사이에 새 형식 메시지가 이전 worker에 도착할 수 있다.
이전 worker는 이것을 해석하지 못해 실패 보관함(dead letter)으로 보낸다.

- 이후로는 worker가 이벤트 종류를 몰라도 되므로 이벤트 추가 때 이 문제가 생기지 않는다.
- 이번 한 번의 유실은 받아들인다(2026-09-24 사용자 결정). PR은 나누지 않는다.
- 배포 도중 Redis에 남아 있는 이전 형식의 `ROOM_APPLICATION_ACCEPTED` 메시지는 새 worker가 처리하지 못하고 실패 보관함으로 간다.
  배포 시점에 outbox에 PENDING으로 남은 이전 형식 행은 새 API가 읽지 못해 `UNREADABLE`로 남는다. 같은 이유로 받아들인다.
- 배포 직전 확인(읽기 전용): `SELECT event_type, relay_status, COUNT(*) FROM outbox GROUP BY 1, 2`
- V30 백필 때문에 배포 직전 3시간 안에 쓰인 리뷰의 공개 알림은 나가지 않는다(decisions.md).

## 영향 범위

- 기존 수락 알림: 채널 동작은 같다. FCM 영구 오류 처리만 "실패 보관" → "전달 안 됨"으로 바뀐다.
- 방 관련 쓰기 트랜잭션: 이벤트 발행과 outbox 저장이 추가된다. 참여자 수가 작아서(방 정원 수준)
  행 수 증가는 작다.
