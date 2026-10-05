# MOI-544 결정 기록

## API 계약 (2026-10-02)

- 엔드포인트를 "설정"(`/notification-setting`)과 "이 기기 등록"(`/web-push-subscriptions`) 두 자원으로 줄였다. 웹 푸시 켜기·끄기 전용 엔드포인트를 두는 초안(5개)은 이해하기 어렵다는 피드백으로 버렸다.
- 설정 변경은 PATCH(보낸 필드만 반영)다. PUT 전체 교체는 열어 둔 화면의 오래된 `webPush` 값이 다른 기기에서 끈 푸시를 되살리고, 기기 기준 화면 표시와 회원 기준 저장값이 엇갈려 기각했다.
- 웹 푸시를 켤 때는 이 기기 토큰을 같은 요청에 싣는다. 켜는 것은 언제나 "이 기기에서 받겠다"는 뜻이라 거부 해제와 등록을 한 트랜잭션으로 묶는다.
- 앱을 열 때의 토큰 갱신은 설정 변경과 분리한다. 설정을 바꾸는 요청에 실으면 브라우저가 들고 있는 지난 설정값이 함께 저장될 수 있다. 갱신은 거부 상태면 무시한다.
- ~~응답·요청 필드는 `webPush`(켜져 있는가)로 노출하고, 저장은 `web_push_opted_out`(껐는가)로 한다.~~ → 아래 "Boolean 이름"으로 대체
- 모킹 컨트롤러 없이 서비스와 바로 연결한다(한 PR).

## 구현·리뷰 반영 (2026-10-02)

- `MemberEntity` 에 `@DynamicUpdate` 를 붙였다. 전체 컬럼 UPDATE 때문에 로그인·닉네임 변경 같은 다른 회원 쓰기가 수신 설정(특히 광고성 정보 철회)을 옛 값으로 되돌릴 수 있었다(db-reviewer). 같은 컬럼 동시 변경은 마지막 쓰기가 이긴다. 레이스 자체는 테스트로 재현하지 않는다(testing.md).
- 구독 쓰기를 `WebPushSubscriptionManager` 로 모았다(`register`·`unregisterAll`). `NotificationSettingManager` 는 구독 Repository 를 보지 않는다.
- 알림 개념이 `MemberRepository` 를 직접 보는 이유를 Finder·Manager 주석에 남겼다(Member 도메인 객체에 수신 설정이 없고 위임할 회원 로직이 없음).
- 반영하지 않은 리뷰 제안(문서 쪽 제안으로 남김): ① layers.md 에 "다른 개념 테이블에 저장되는 자기 개념 데이터의 직접 접근" 사례 추가 ② layers.md 에 "단일 개념이면 쓰기 Service 가 재조회 결과를 반환해도 된다" 명시(JobPostingService 선례) ③ api-design.md 에 "회원당 하나뿐인 하위 자원은 단수형(/profile, /notification-setting)" 예외 명시.
- 알려진 한계: 끄기와 계정 전환 upsert 가 동시에 오면 드물게 InnoDB 교착으로 한쪽이 500 이 날 수 있다(정합성은 유지, db-reviewer "불확실"). 대응 코드 없음.

## Boolean 이름 (2026-10-02 사람 결정)

- Boolean 필드는 `is`/`has`/`can` 접두사 + 긍정형으로 짓는다. 부정형(`webPushOptedOut`)은 `!` 이중 부정과 API 값 뒤집기를 만든다.
- 바꾼 이름: DB `is_web_push_allowed`(기본 TRUE)·`is_activity_email_enabled`·`is_marketing_email_agreed`(`marketing_email_agreed_at` 은 그대로), 엔티티·도메인·API·worker `isWebPushAllowed`·`isActivityEmailEnabled`·`isMarketingEmailAgreed`, 엔티티 메서드 `allowWebPush`/`disallowWebPush`, 변경 개념 `WebPushChange.Allow`/`Disallow`.
- 뜻은 그대로다: 회원 단위로 "웹 푸시를 끄지 않았는가"를 저장하고, 실제 수신은 기기 등록이 함께 있어야 한다. V32 는 아직 배포 전이라 파일을 직접 고쳤다.

## PR 전 QA 리뷰 반영 (2026-10-02 사람 결정)

- 웹 푸시를 끈 회원의 기기 등록 갱신이라도, 그 등록이 다른 회원 소유면 지운다(`WebPushSubscriptionManager.unregisterIfOwnedByOther`). 같은 브라우저를 쓰던 앞사람의 알림이 계속 뜨는 것을 막는다. 끈 회원에게는 여전히 아무것도 저장하지 않는다. 지금도 남의 등록 식별자를 알면 upsert 로 소유를 가져올 수 있어 새 권한이 생기지 않는다. PRD R167 추가.
- 요청·응답의 `is` 접두 필드를 앱 매퍼로 검사하는 계약 테스트를 `ResponseSerializationContractIT` 에 추가했다(MOI-500 회귀 재발 방지).
- 배포 순서: core-api(Flyway V32) 먼저, worker 나중. worker 는 Flyway 를 돌리지 않아 먼저 뜨면 새 컬럼이 없어 실패한다.
