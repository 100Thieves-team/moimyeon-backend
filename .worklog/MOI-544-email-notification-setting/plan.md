# MOI-544 알림 수신 설정 계획

결정 근거: [tbd.md](tbd.md) "결정됨". 설계 정리: https://claude.ai/artifact/1g6zVsci9HwFHvbigk9MBg

## 단계

- [x] 1. 컨텍스트 수집과 설계 결정 (context.md, tbd.md) — 2026-10-02 사람 확인
- [x] 2. wiki-sync: 회원 PRD 수신 설정 절 신설, 상태 SSOT 규칙 추가, 주제 페이지 MVP 표현 수정 (wiki-sync.md)
- [x] 3. 엔티티 설계: 수신 설정 저장 구조, Flyway 마이그레이션(V32~), schema.sql
  - 1단 논리 모델(erd.dbml) 사람 합의 2026-10-02: member 컬럼 4개
  - 2단 구현: V32 마이그레이션, schema.sql, MemberEntity 매핑. db-reviewer 차단 없음(INSTANT, 롤백 호환), 기본값 회귀 테스트 추가 반영
  - 검증: `./gradlew test ktlintCheck` 통과, MySqlSchemaValidationIT(MySQL 8.4 Testcontainers, Flyway V32 적용) 12건 통과
- [x] 4. API 계약 (v4 스펙 승인, 구현 완료, 2026-10-02 승인): 수신 설정 조회·변경, 웹 푸시 켜기·끄기·토큰 갱신. 기존 `/v1/members/me/web-push-subscriptions` 제거
- [x] 5. 서비스 구현 (TDD, 구현 완료, 2026-10-02 승인): 설정 변경, 푸시 거부 시 기기 삭제, 거부 상태 등록 무시, 광고 동의 시각
- [x] 6. worker 반영 (구현 완료, 2026-10-02 승인): 받을 수 있는 채널 계산(푸시 거부·메일 꺼짐)
- [ ] 7. 리뷰·구현 후 wiki-sync 대조·PR

## 4단계 API 계약 (v4, 2026-10-02 사람 승인)

근거: 회원 PRD §4.10(R140~R162), 상태 SSOT `P.notification.receive_setting`. 와이어프레임 없음 — PRD 가 근거다.
인증: 전부 로그인 회원(`@LoginMember`). 한 PR로 묶기로 해 모킹 컨트롤러는 만들지 않고 서비스와 바로 연결한다.

| # | 메서드·경로 | 행위 | SSOT 명령 |
| --- | --- | --- | --- |
| 1 | `GET /v1/members/me/notification-setting` | 수신 설정 조회 | — |
| 2 | `PATCH /v1/members/me/notification-setting` | 보낸 토글만 바꾼다 | update_notification_setting · enable_web_push · disable_web_push |
| 3 | `PUT /v1/members/me/web-push-subscriptions` | 기기 등록 갱신(기존 API). 웹 푸시가 꺼져 있으면 저장하지 않고 성공 | refresh_web_push |

기존 `DELETE /v1/members/me/web-push-subscriptions` 는 제거한다(끄기는 2번).

### 1 응답 `NotificationSettingResponse` (2도 같은 응답)

| 필드 | 타입 | 뜻 |
| --- | --- | --- |
| `isWebPushAllowed` | Boolean | 웹 푸시를 허용했는지(끄지 않았는지). 화면 토글은 이 값 + 브라우저 권한 + 이 브라우저 토큰으로 프론트가 정한다 |
| `isActivityEmailEnabled` | Boolean | 서비스 활동 알림 메일 |
| `isMarketingEmailAgreed` | Boolean | 광고성 정보 수신 동의 |
| `marketingEmailAgreedAt` | LocalDateTime? | 마지막 동의 시각. 동의한 적 없으면 null |

### 2 요청 `UpdateNotificationSettingRequest` — 모든 필드 선택, 보낸 것만 반영

| 필드 | 타입 | 규칙 |
| --- | --- | --- |
| `isWebPushAllowed` | Boolean? | true 면 `webPushRegistration` 필수 → 허용 + 이 기기 등록. false 면 허용 해제 + 모든 기기 등록 삭제 |
| `webPushRegistration` | String? | `isWebPushAllowed=true` 일 때만 받는다 |
| `isActivityEmailEnabled` | Boolean? | |
| `isMarketingEmailAgreed` | Boolean? | false→true 로 바뀔 때 동의 시각 기록 |

### 3 요청 — 기존 `WebPushSubscriptionRequest(registration: String)`, 응답 본문 없음

### 에러

| 상황 | 엔드포인트 | 코드 |
| --- | --- | --- |
| 바꿀 필드가 하나도 없음 | 2 | 400 `E400` |
| `isWebPushAllowed=true` 인데 `webPushRegistration` 없음, 또는 `isWebPushAllowed` 가 true 가 아닌데 `webPushRegistration` 있음 | 2 | 400 `E400` |
| 등록 식별자 공백 | 2·3 | 400 `E1601` (기존) |
| 본문 해석 실패·타입 불일치 | 2·3 | 400 `E400` |
| 미인증 | 전부 | 기존 인증 에러 |

광고성 정보 동의·철회 결과(R162)는 응답의 `isMarketingEmailAgreed`·`marketingEmailAgreedAt` 으로 화면이 보여 준다. 철회 일자는 응답 시점 날짜다.

## 5·6단계 구현 계획 (2026-10-02 사람 승인 — /concept-bulkhead 로 테스트 먼저 진행)

> 이 절의 필드·메서드 이름은 이후 Boolean 이름 규칙으로 바뀌었다(decisions.md "Boolean 이름"). 현재 이름은 위 API 계약 표와 코드를 따른다.

### 접근

- 개념: 수신 설정은 `notification` 개념이다. 저장 위치만 `member` 행이다(엔티티 설계 결정). `core.domain.notification` 에 둔다.
- 흐름: Service 는 Implement 한 줄 위임. 쓰기 경계·판정은 `NotificationSettingManager` 가 갖는다.
- 웹 푸시 거부 판정은 설정값으로 직접 한다. 기기 삭제는 토큰 정리다.

### 변경 지점

core-api (`core.domain.notification`)
- `NotificationSetting` — 조회 결과 도메인 값(거부 여부·메일·광고·동의 시각)
- `NotificationSettingChange` + `WebPushChange`(`Enable(registration)` / `Disable`) — PATCH 입력 개념 객체. 바꾸지 않는 항목은 null
- `NotificationSettingFinder.get(memberId)` — 회원 행에서 읽어 변환. 없는 회원은 `MEMBER_NOT_FOUND`
- `NotificationSettingManager` (`@Transactional`)
  - `change(memberId, change)`: 메일·광고 반영(광고 false→true 일 때만 동의 시각), `Enable` → 거부 해제 + 기기 upsert, `Disable` → 거부 + 회원의 기기 전부 삭제
  - `refreshWebPush(memberId, registration)`: 거부 상태면 아무것도 쓰지 않음, 아니면 기기 upsert
  - 기기 upsert 는 기존 `WebPushSubscriptionManager.register`(해시 충돌 검사 포함)를 재사용
- `NotificationSettingService`: `get`, `change`(변경 후 재조회 결과 반환), `refreshWebPush`
- 제거: `WebPushSubscriptionService`, `WebPushSubscriptionManager.unregister`
- `WebPushSubscriptionRepository.deleteAllByMemberId` 추가

storage (`MemberEntity`)
- 상태 변경 메서드: `optOutWebPush()`, `allowWebPush()`, `changeActivityEmail(enabled)`, `changeMarketingEmail(agreed, now)`

API (`core.api.controller.v1`)
- `NotificationSettingController`: `GET`·`PATCH /v1/members/me/notification-setting`, `PUT /v1/members/me/web-push-subscriptions`
- `WebPushSubscriptionController` 제거(PUT 은 위 컨트롤러로 이동, DELETE 제거)
- `UpdateNotificationSettingRequest.toChange()` — 빈 요청·`webPush`/`webPushRegistration` 짝 위반은 `E400`
- `NotificationSettingResponse.from(setting)` — `webPush = !webPushOptedOut`

worker (`core-worker` notification delivery)
- `NotificationRecipient` 에 `webPushOptedOut`, `activityEmailEnabled` 추가
- `MemberNotificationRecipientFinder` 가 두 값을 읽는다
- `ChannelNotificationSender`: 푸시 = 거부 아님 + 기기 있음일 때만, 대신 메일·메일 메시지 = 메일 켜짐일 때만

### 테스트 목록

- `NotificationSettingServiceTest`(mockk): 조회 위임, 변경 후 재조회, 갱신 위임, Manager 예외 전파
- `NotificationSettingManagerIT`(ContextTest, 바깥 트랜잭션 없음)
  - 메일 수신을 끄면 저장된다 / 보내지 않은 항목은 바뀌지 않는다
  - 광고성 정보에 처음 동의하면 동의 시각을 남긴다 / 이미 동의한 상태에서 다시 동의해도 시각은 그대로다 / 철회해도 마지막 동의 시각은 지우지 않는다
  - 웹 푸시를 켜면 거부를 풀고 이 기기를 등록한다
  - 웹 푸시를 끄면 회원의 모든 기기 등록을 지우고 다른 회원 기기는 남긴다
  - 웹 푸시가 꺼져 있으면 기기 등록 갱신은 저장하지 않는다 / 켜져 있으면 저장한다
  - 없는 회원은 `MEMBER_NOT_FOUND`
- `NotificationSettingControllerTest`(RestDocs): 조회 성공, 변경 성공(메일·광고·푸시 켜기·끄기), 빈 요청 E400, 푸시 켜기 토큰 누락 E400, 토큰만 보냄 E400, 토큰 공백 E1601, 갱신 성공, 갱신 토큰 공백 E1601
- worker `ChannelNotificationSenderTest`: 거부 회원은 기기가 있어도 푸시 안 보냄 → PUSH_ELSE_EMAIL 이면 메일 / 메일 꺼짐이면 메일 메시지·대신 메일 모두 안 보냄 / 둘 다 꺼지면 아무것도 안 보냄
- worker `MemberNotificationRecipientFinderTest`: 설정값을 함께 읽는다
- 제거·수정: `WebPushSubscriptionControllerTest`, `WebPushSubscriptionManagerTest`·`IT` 의 해지 케이스

### API 문서·외부 영향

- `core/core-api/src/docs/asciidoc/index.adoc` 에 알림 수신 설정 절 추가, 기존 웹 푸시 구독 해지 문서 제거
- 프론트: `DELETE /web-push-subscriptions` 제거, `GET`·`PATCH /notification-setting` 신규, `PUT /web-push-subscriptions` 는 경로·요청 그대로지만 꺼진 상태면 저장되지 않는다. PR 본문에 프론트 변경 안내를 적는다
- worker 배포: API 와 worker 를 함께 배포한다. API 만 먼저 배포돼도 worker 는 거부값을 아직 모를 뿐 기존처럼 동작한다(dev 라 유실·순서 무시)

### 영향 범위

알림 발송 전체(채널 판정), 웹 푸시 등록 API, 회원 엔티티. 룸·신청 도메인과 알림 생성(`NotificationComposer`)은 바꾸지 않는다.

## 4~6단계 결과 (체크포인트 C 대기)

- 리뷰: code-reviewer 필수 0·권장 4(모두 반영)·참고 7(3건 반영, 문서 제안 2건은 decisions.md), db-reviewer 중간 1(`@DynamicUpdate` 반영)·낮음 2(주석 반영, 교착은 알려진 한계)
- 검증: `./gradlew test restDocsTest ktlintCheck :core:core-api:openapi3` 통과 (MySqlSchemaValidationIT 포함)
