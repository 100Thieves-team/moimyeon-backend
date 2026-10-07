# MOI-569 메일 전송 template 추가 — 컨텍스트

## 이슈 요약

[MOI-569](https://linear.app/100-thieves/issue/MOI-569/메일-전송-template-추가) (Todo, 라벨·프로젝트·부모 이슈·커멘트 없음)

지금 알림 메일은 제목 한 줄 + 본문 텍스트 + 빈 줄 두 개 + 링크만 보내는
평문(plain text)이다. 이슈 본문에 붙은 dev 수신 예시가 그대로 그 형태다.

```
[QA] 준비 작업 룸 fe8280'에 참가 신청이 들어왔어요. 신청 내용을 확인해 주세요.

https://dev.moimyeon.plady.io/rooms/e89da2d2-03ee-4d01-8d0d-e9badde0e74d
```

요구사항은 한 줄이다 — "단순 텍스트가 아닌, 약간의 디자인이 적용된 이메일
템플릿을 적용하여 전송한다." 어떤 디자인인지는 이슈에 없다.

예시의 링크 경로(`/rooms/...`)는 MOI-567에서 `/interviews/...`로 고쳤다.
이슈 본문은 그 수정 전에 수집된 것이다.

## 관련 제품 명세

LLM Wiki에서 이메일 **본문 형식·디자인**을 정한 문서는 찾지 못했다.
[방 상태 및 알림 정책](wiki://100thieves/topics/t-moimyeon-방-상태-및-알림-정책)은
어느 채널로 보낼지(수신 설정·발송 정책)만 정하고 메일이 어떻게 보이는지는
다루지 않는다. 문구 자체는 코드(`NotificationComposer`)가 단일 소스다.

따라서 이 작업의 제품 결정은 비어 있다 — `tbd.md` 참조.

## 관련 코드

### 메일이 만들어지는 곳 (변경 중심)

- `clients/email-client/src/main/kotlin/io/plady/moimyeon/client/email/FailoverEmailSender.kt`
  — `Notification`을 `EmailMessage`로 바꾸는 유일한 지점. 지금 본문은
  `listOfNotNull(body, actionUrl).joinToString("\n\n")`이다.
- `clients/email-client/src/main/kotlin/io/plady/moimyeon/client/email/EmailDeliveryProvider.kt`
  — `EmailMessage(to, subject, body)`. HTML 본문 개념이 없다.
- `clients/email-client/src/main/kotlin/io/plady/moimyeon/client/email/SesEmailDeliveryProvider.kt`
  — SESv2 `Body.text(...)`만 채운다. HTML은 `Body.html(...)`로 함께 보낼 수 있다.
- `clients/email-client/src/main/kotlin/io/plady/moimyeon/client/email/GmailSmtpEmailDeliveryProvider.kt`
  — `SimpleMailMessage`를 쓴다. HTML을 보내려면 `MimeMessageHelper`(멀티파트)로 바꿔야 한다.
- `clients/email-client/src/main/kotlin/io/plady/moimyeon/client/email/EmailClientConfiguration.kt`
  — 두 공급자와 `FailoverEmailSender` 조립, `notification.email.*` 설정 바인딩.
- `clients/email-client/src/main/resources/email-client.yml` — SES·Gmail 설정.
- `clients/email-client/build.gradle.kts` — 현재 의존: `core-enum`, `core-worker`,
  `spring-boot-starter-mail`, AWS `sesv2`. 템플릿 엔진 의존은 없다.

### 메일을 호출하는 쪽 (읽기만)

- `core/core-worker/.../notification/delivery/EmailSender`(`NotificationSender.kt` 안)
  — `send(notification, recipient)` 계약. email-client가 구현한다.
- `core/core-worker/.../notification/delivery/Notification.kt`
  — `NotificationContent(title, body, actionUrl)`. 메일이 쓸 수 있는 재료의 전부다.
- `core/core-worker/.../notification/delivery/ChannelNotificationSender.kt`
  — 채널 분기와 `PUSH_ELSE_EMAIL` 폴백. 메일 호출 지점.
- `core/core-worker/src/main/resources/application.yml`
  — `notification.action-base-url`(프로필별 프론트 주소). 링크의 절대 URL이 여기서 나온다.

### 문구가 정해지는 곳 (읽기만, core-api)

- `core/core-api/src/main/kotlin/io/plady/moimyeon/core/notification/NotificationComposer.kt`
  — 11종 이벤트의 제목·본문·`actionPath`를 한 곳에서 정한다. worker는 이벤트 종류를
  모르고 공통 형식만 해석한다(MOI-499).

### 기존 테스트

- `clients/email-client/src/test/.../FailoverEmailSenderTest.kt`
  — `EmailMessage` 전문을 `containsExactly`로 비교한다. 본문 형식을 바꾸면 깨진다.
- `clients/email-client/src/test/.../SesEmailDeliveryProviderTest.kt`,
  `GmailSmtpEmailDeliveryProviderTest.kt`
- `core/core-worker/src/test/.../room/RoomApplicationAcceptedNotificationFlowIT.kt`
  — 신청 수락 알림의 end-to-end 흐름.

### 참고할 수 있는 선례

- `admin/admin-api`가 Thymeleaf(`spring-boot-starter-thymeleaf`)로 SSR 화면을 렌더링한다.
  단, clients 모듈에 같은 의존을 넣는 것은 별개 판단이다.
- 저장소에 로고·브랜드 이미지 자산이 없다. 메일에 쓸 이미지는 외부 호스팅이 필요하다.

## 모듈·레이어 제약

- `clients:email-client` → `core-worker`, `core-enum` 컴파일 의존.
  `core-worker` → `clients:email-client`는 **runtimeOnly**. 이 방향을 유지한다.
- 발송 수단의 상세(HTML 조립, 템플릿 엔진)는 clients 격벽 안에 둔다.
  worker가 HTML을 알게 되면 격벽이 깨진다.
- `NotificationContent`를 늘리는 변경은 core-api(`NotificationComposer`)와
  redis-core 메시지 형식까지 번진다. API를 worker보다 먼저 배포하는 현재 순서에서
  호환성을 따져야 한다.

## 이 작업의 경계

- 알림 **문구**는 바꾸지 않는다. `NotificationComposer`는 건드리지 않는다.
- 발송 **정책·채널·수신 설정**은 바꾸지 않는다.
- 웹 푸시는 범위 밖이다.
- SES 도메인 인증·DKIM 같은 인프라 설정은 범위 밖이다.
- 광고성 메일·약관 안내 같은 다른 메일 종류는 범위 밖이다(지금 없다).
