# MOI-544 컨텍스트

## 이슈 요약

[MOI-544 알림/메일 수신 설정 API/로직 구현](https://linear.app/100-thieves/issue/MOI-544)은
사용자가 푸시 알림과 메일을 받을지 직접 끄고 켤 수 있게 하는 작업이다. 지금은 웹에서
service worker로 FCM 구독을 한 뒤에는 수신을 거부할 방법이 없고, 메일도 같다.

이슈가 요구하는 것:
- 메일/알림 수신 동의 여부를 저장·수정하는 API
- 설정값에 따라 기존 알림 발송 로직 검토·수정
- 관련 PRD와 정책을 LLM Wiki에 추가

## 근거 문서

- [2026-10-01 proj-moimyeon 허들 원문](wiki://100thieves/raw/meetings/slack-huddle-2026-10-01-proj-moimyeon-0q9v6d):
  "알림/이메일 수신 설정 페이지 필요성 제안 → 설정 페이지는 MVP에서 제외하고 추후 추가".
  작업 항목에 "알림 설정 API를 구현하겠다"가 있다.
- [topics/t-moimyeon-방-상태-및-알림-정책](wiki://100thieves/topics/t-moimyeon-방-상태-및-알림-정책) §알림과 수신 설정:
  "사용자별 수신 설정 UI는 후속 범위로 분리". 확인 필요 항목에 "PRD와 이슈에서도 후순위로 분리" 언급.
- [PRD 회원 및 프로필](wiki://100thieves/raw/product/회원-및-프로필): 수신 설정 항목이 **없다**.
  §6 수집 데이터에 약관 동의 이력(`R110`)만 있다.
- 상태 SSOT `policy/정책`: `P.notification.*`는 이벤트별 수신 대상만 정한다. 수신 거부 규칙은 없다.

즉 이 기능의 제품 명세는 Wiki에 아직 없다. 이슈가 Wiki 추가를 요구하므로 구현 전에 `wiki-sync`로 만든다.

## 현재 알림 구조 (요약)

도메인 Manager가 outbox 이벤트 저장 → `NotificationOutboxEventConsumer` → `NotificationComposer`가
받는 사람·정책·문구 결정 → Redis Stream(채널마다 1건) → worker `NotificationMessageHandler` →
`ChannelNotificationSender`가 FCM 또는 메일 발송. 앱 안 알림 목록(알림함)은 없다.

이벤트별 정책(`NotificationComposer.kt`):

| 이벤트 | 정책 |
| --- | --- |
| 참여 신청 | PUSH_ELSE_EMAIL |
| 신청 수락 | PUSH_AND_EMAIL |
| 신청 반려 | PUSH_ELSE_EMAIL |
| 방 확정 | 참여자 PUSH_AND_EMAIL / 마감된 대기 신청자 PUSH_ELSE_EMAIL |
| 방 완료(후기 요청) | PUSH_ELSE_EMAIL |
| 방 취소 | PUSH_AND_EMAIL |
| 방장 위임 | PUSH_ELSE_EMAIL |
| 후기 공개 | PUSH_ONLY |
| 댓글 작성 | PUSH_ONLY |

메일은 이 알림 파이프라인으로만 나간다(마케팅·리마인더 메일 없음).

## 관련 코드 위치

수신 설정이 붙을 자리
- `core/core-worker/.../notification/delivery/MemberNotificationRecipientFinder.kt` — 받는 사람의 메일·푸시 기기를 읽는다. 설정을 반영하기 가장 자연스러운 곳
- `core/core-worker/.../notification/delivery/ChannelNotificationSender.kt` — 채널 선택. 16행 푸시 실패 시 메일 대체, 28행 "기기 없음" 판정
- `core/core-worker/.../notification/delivery/NotificationRecipient.kt` — `NotificationRecipient(email, webPushRegistrations)`
- `core/core-api/.../core/notification/NotificationComposer.kt` — 이벤트별 받는 사람·정책
- `core/core-enum/.../enums/NotificationPolicy.kt`, `NotificationChannel.kt`

푸시 구독(기기 등록)
- `core/core-api/.../controller/v1/WebPushSubscriptionController.kt` — `PUT`/`DELETE /v1/members/me/web-push-subscriptions`
- `core/core-api/.../domain/notification/WebPushSubscriptionService.kt`, `WebPushSubscriptionManager.kt`
- `storage/db-core/.../WebPushSubscriptionEntity.kt`, `schema.sql:337` (`web_push_subscription`, 해지 시 행 삭제)

회원·저장소
- `storage/db-core/.../MemberEntity.kt` — `email`, `nickname`, `status`, `role`, `lastLoginAt`. 동의·설정 필드 없음
- `storage/db-core/.../TermsAgreementEntity.kt` — 약관 동의 이력(추가만 하는 기록). `TermsType`은 `SERVICE`, `PRIVACY`뿐
- 마지막 Flyway 마이그레이션 `V31__index_review_notification_candidates.sql` → 다음은 V32

본보기가 될 `me` 수정 API
- `ProfileController.kt` `PUT /v1/members/me/profile` → `ProfileFacade` → `ProfileService` → `ProfileUpdater`
- `MemberController.kt` `GET /v1/members/me` → `MemberMeResponse` (설정값을 여기 실을지 결정 필요)
- 테스트: `ProfileControllerTest.kt`, `MemberControllerTest.kt`, `WebPushSubscriptionControllerTest.kt`
- 문서: `core/core-api/src/docs/asciidoc/index.adoc` Member API(195행)·Profile API(282행)

에러 코드
- `core/core-api/.../support/error/ErrorCode.kt` — 회원 E10xx, 알림 E16xx(E1601만 사용)
- `core/core-api/.../support/error/CoreErrorType.kt`

참고 worklog: `.worklog/MOI-499-notification-policy/` (알림 정책 구조와 결정)

## 이 작업의 경계 (하지 않는 것)

- 설정 화면(프론트) 구현. 화면까지 MVP 범위지만 프론트 작업이다(2026-10-02 결정, tbd.md).
- 앱 안 알림 목록(알림함).
- 광고성 정보 발송 기능과 2년 주기 재확인. 동의 여부와 마지막 동의 시각 저장은 범위 안이다(tbd.md).
- 알림 문구·이벤트별 정책 자체의 변경.
