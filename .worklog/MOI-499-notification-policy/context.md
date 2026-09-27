# MOI-499 컨텍스트

## 이슈 요약

[MOI-499 FCM 알림 전송 누락된 곳 추가하기](https://linear.app/100-thieves/issue/MOI-499)는
이벤트마다 알림을 푸시로 보낼지, 메일로 보낼지, 둘 다 보낼지를 정책으로 정하고,
지금 알림이 빠져 있는 이벤트들에 알림을 붙이는 작업이다.

정책은 네 가지다.

| 정책 | 동작 |
| --- | --- |
| `PUSH_ONLY` | 푸시만 보낸다. 받을 기기가 없으면 아무것도 보내지 않는다 |
| `PUSH_ELSE_EMAIL` | 푸시를 보내 보고, 한 기기에도 전달되지 않았으면 메일을 보낸다 |
| `EMAIL_ONLY` | 메일만 보낸다 |
| `PUSH_AND_EMAIL` | 푸시와 메일을 둘 다 보낸다 |

"푸시가 전달됐다"의 기준(이슈 원문):
- 기기 중 하나라도 FCM 전송에 성공하면 전달된 것으로 본다.
- 등록된 기기 토큰이 아예 없거나, 보내 보니 전부 `UNREGISTERED`(브라우저에서 구독이 지워진 토큰)이면
  전달되지 않은 것으로 본다.

PRD: 알림 정책만 다루는 PRD는 Notion에 없다. 관련 언급은
[룸 참여 및 참여자 관리](https://app.notion.com/p/2461bb8f1fbe820191de81155ce017ab)
("신청자에게 반려 결과를 알린다"), [룸 방명록](https://app.notion.com/p/39e1bb8f1fbe80489310d1df4222556e)
§4.5 알림 정도다. 팀 위키 MCP는 이번 세션에서 인증 실패(401)로 조회하지 못했다.

## 대상 이벤트 (이슈 표, 2026-09-24 사용자 확인 반영)

| 이벤트 | 받는 사람 | 정책 |
| --- | --- | --- |
| 참여 신청 | 방장 | PUSH_ELSE_EMAIL |
| 참여 신청 수락 | 신청자 | PUSH_AND_EMAIL (이미 구현됨) |
| 참여 신청 반려 | 신청자 | PUSH_ELSE_EMAIL |
| 방 확정 | 참여자 전체 | PUSH_AND_EMAIL |
| 방 완료(리뷰 요청) | 참여자들 | PUSH_ELSE_EMAIL |
| 방 취소(모집 중) | 대기 중이던 신청자 | PUSH_AND_EMAIL |
| 방장 위임 | 참여자 전체 | PUSH_ELSE_EMAIL |
| 리뷰 작성 | 리뷰 받은 사람 | PUSH_ONLY |
| 댓글 작성 | 참여자 전체 | PUSH_ONLY (메일 몰아 보내기는 후속 이슈) |

사용자 결정(2026-09-24):
- 범위: 정책 구조 + 위 이벤트 전부. 댓글 메일 몰아 보내기는 이번 범위 밖.
- 여러 명에게 가는 알림은 **이벤트를 발행하는 시점에 받는 사람마다 하나씩** 만든다. (이후 변경: outbox 에는 사실 1건을 저장하고, 받는 사람별로 나뉘는 것은 Redis 메시지다 — decisions.md)
- 방 취소 알림은 대기 중이던 신청자에게 보낸다. 방 취소는 모집 중에만 일어나고
  그 시점엔 남은 참여자가 없기 때문이다.

## 현재 알림 전송 구조

API 서버와 worker(알림을 실제로 보내는 별도 프로세스)로 나뉘어 있다.

1. 도메인 코드가 트랜잭션 안에서 Spring 이벤트를 발행한다.
2. `NotificationRelay`가 커밋 직전에 이벤트를 `outbox` 테이블에 저장한다(outbox 패턴:
   업무 데이터와 같은 트랜잭션에 "보낼 알림"을 기록해 두고 나중에 전달하는 방식).
   커밋 뒤에는 Redis Stream으로 발행한다. 발행에 실패한 행은 `PendingOutboxRelayScheduler`가
   10초마다 다시 발행한다.
3. `RedisNotificationMessagePublisher`는 이벤트 종류에 정해진 채널(WEB_PUSH, EMAIL)마다
   Redis 메시지를 하나씩 넣는다. 채널별로 따로 재시도된다.
4. worker의 `NotificationMessageHandler`가 메시지를 읽어 제목·본문·링크를 만들고,
   `ChannelNotificationSender`가 채널에 따라 `FcmWebPushSender` 또는 메일 발송기로 보낸다.

관련 코드:
- `core/core-enum/.../EventType.kt`: 이벤트 종류와 채널 목록. 현재 `ROOM_APPLICATION_ACCEPTED` 하나
- `core/core-api/.../notification/outbox/`: outbox 저장·Redis 발행·재발행 스케줄러
- `storage/redis-core/.../RedisNotificationMessagePublisher.kt`: 채널별 Redis 메시지 발행
- `storage/redis-core/.../RedisNotificationStreamConsumer.kt`: 읽기·재시도·실패 메시지 보관(dead letter)
- `core/core-worker/.../notification/NotificationMessageHandler.kt`: 이벤트별 알림 내용 생성
- `core/core-worker/.../notification/delivery/ChannelNotificationSender.kt`: 채널별 발송
- `clients/web-push-client/.../FcmWebPushSender.kt`: FCM 전송, 결과별 예외, 만료 토큰 삭제
- `clients/email-client/.../FailoverEmailSender.kt`: SES 실패 시 Gmail SMTP로 넘기는 메일 발송

## 이벤트가 발생하는 코드 위치

| 이벤트 | 위치 |
| --- | --- |
| 참여 신청 | `core/core-api/.../roomapplication/RoomApplicationSubmissionManager.submit` |
| 수락·반려 | `core/core-api/.../room/RoomApplicationManager.accept` / `reject` |
| 확정 | `core/core-api/.../room/RoomManager.confirm` |
| 취소 | `RoomManager.cancelWithoutGuard` (방장 직접 취소, 방장 나가기 후 넘겨받을 사람 없음 두 경로 공용) |
| 방장 위임 | `core/core-api/.../room/RoomLeaveManager.delegateOrCancel` (참여자 → 대기 신청자 순) |
| 완료 | ① `core/core-api/.../closing/ClosingSubmissionManager` (출석자 전원 클로징 제출)<br>② `core/core-worker/.../room/OverdueRoomCompleter` (시작 +8시간 자동 완료, worker 프로세스) |
| 리뷰 작성 | `core/core-api/.../trust/ReviewSubmissionManager.submit` |
| 댓글 작성 | `core/core-api/.../roomcomment/RoomCommentService.leaveComment` → `RoomCommentManager.post` |

주의:
- worker 모듈은 core-api에 의존하지 않는다. 자동 완료 경로는 core-api의 `NotificationRelay`를
  쓸 수 없고 `outbox` 행을 직접 저장해야 한다(`OutboxEntity`는 db-core에 있어 worker도 쓸 수 있다).
- 확정도 대기 중인 신청을 일괄 종료한다(`ROOM_CONFIRMED`). 이 신청자들에게 알릴지는 이슈에 없다.

## 이 작업의 경계 (하지 않는 것)

- 확정된 방을 없애는 기능("방 폭파"): 기능 자체가 없다.
- 댓글 알림 메일 몰아 보내기: 후속 이슈.
- 알림 목록 화면(앱 내 알림함), 알림 수신 설정(사용자별 on/off).
- API 계약 변경: 없다.
