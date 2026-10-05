# MOI-569 메일 전송 template 추가 — 구현 계획

이슈·코드 위치는 [context.md](context.md), 남은 미결정은 [tbd.md](tbd.md).

## 사람이 정한 것

- 시안은 구현자 재량. CSS를 제대로 적용한다.
- **공통 레이아웃 1개.** 알림 종류별 템플릿은 만들지 않는다.
- 공통 레이아웃 안에 공통 형식(제목·본문·버튼 등)을 정의한다.

이 결정으로 변경이 `clients:email-client` 안에서 끝난다. `NotificationContent`,
`NotificationComposer`(core-api), redis-core 메시지 형식을 건드리지 않으므로
API→worker 배포 순서 호환성 문제가 없다.

## 접근 방식

### 1. 레이아웃은 classpath 리소스 HTML + 자리표시자 치환

Thymeleaf를 `email-client`에 새로 넣지 않는다. 템플릿 1개에 값 3개를 끼우는
일에 템플릿 엔진 의존을 clients 격벽으로 들이는 건 과하다. 대신
`{{name}}` 자리표시자를 치환하는 작은 렌더러를 둔다.

**치환 함수가 모든 값을 HTML 이스케이프한다.** 룸 제목은 사용자 입력이고
본문 문구에 그대로 들어간다(`'${payload.roomTitle}'`). 이스케이프를 호출
규율이 아니라 치환 메커니즘의 성질로 만들어 빠뜨릴 수 없게 한다.

리소스를 별도 파일로 두면 CSS를 HTML 문법 강조 아래에서 편집할 수 있고,
나중에 디자이너 시안이 나와도 Kotlin 코드를 안 건드린다.

### 2. 메일 HTML 작성 규칙

메일 클라이언트는 브라우저가 아니다. 아래를 지킨다.

- **table 기반 레이아웃** — Outlook은 `div` + `float`/`flex`를 제대로 못 그린다.
- **모든 스타일을 inline `style` 속성으로** — Outlook.com 등이 `<style>`을 지운다.
- **버튼은 `td` 배경색 + `a` 패딩**("bulletproof button") — Outlook은 `a`의
  `padding`을 무시한다.
- **고정 폭 600px** + `max-width`, 바깥은 회색 배경에 가운데 정렬.
- **외부 이미지를 쓰지 않는다** — 저장소에 로고 자산이 없고, 이미지 차단·
  스팸 점수 문제도 피한다. 워드마크는 텍스트로 쓴다.
- **`color-scheme: light only` 메타** — 일부 클라이언트의 강제 다크 반전으로
  대비가 깨지는 것을 막는다.
- **한글 폰트 스택** — `Apple SD Gothic Neo`, `Malgun Gothic`을 앞에 둔다.
- **preheader**(숨긴 한 줄) — 받은 편지함 미리보기에 본문이 보이게 한다.

### 3. 공통 형식 — 레이아웃이 정의하는 슬롯

`NotificationContent`가 주는 값은 `title`·`body`·`actionUrl` 셋뿐이다.
"공통 형식"은 이 셋을 담는, 이름과 스타일이 고정된 슬롯이다.

| 슬롯 | 태그 | 스타일 |
| --- | --- | --- |
| 워드마크 | `span` | 15px / 700 / 브랜드색. 헤더에 고정 |
| 알림 제목 | `h1` | 22px / 700 / `#111827` / line-height 1.4 ← `title` |
| 본문 단락 | `p` | 15px / 400 / `#374151` / line-height 1.7 ← `body` |
| CTA 버튼 | `table` + `a` | 흰 글자 / 브랜드색 배경 / radius 8px ← `actionUrl` |
| 대체 주소 | `p` + `a` | 13px / `#6B7280`. 버튼이 안 눌릴 때의 생 URL |
| 푸터 | `p` | 12px / `#9CA3AF`. 발송 사유 안내 |

제목은 메일 문서의 유일한 최상위 제목이라 `h2`가 아니라 `h1`로 둔다.
색은 전부 리소스 파일 상단 한 곳에서 찾을 수 있게 모아 두고, 시안이 나오면
그 값만 바꾼다.

서비스 표시 이름은 **모이면**으로 쓴다 — 어드민 화면 제목
(`알림 운영 현황 | 모이면 Admin`)에 이미 쓰는 이름이다.

### 4. HTML과 평문을 함께 보낸다 (multipart/alternative)

HTML만 보내면 평문 클라이언트에서 깨지고 스팸 점수가 올라간다.
평문 대체 본문은 **지금 쓰는 형식을 그대로** 유지한다
(`listOfNotNull(body, actionUrl).joinToString("\n\n")`). 따라서 평문 수신자가
보는 메일은 이번 변경 전후가 같다.

## 변경 지점

### 새로 만드는 것

| 파일 | 역할 |
| --- | --- |
| `clients/email-client/src/main/resources/email/notification-email.html` | 공통 레이아웃. 자리표시자 `{{preheader}}` `{{title}}` `{{body}}` `{{action}}` |
| `clients/email-client/src/main/resources/email/notification-email-action.html` | CTA 버튼 + 대체 주소 조각. `actionUrl`이 있을 때만 `{{action}}`에 들어간다 |
| `.../client/email/NotificationEmailTemplate.kt` | 두 리소스를 읽어 `NotificationContent` → `EmailMessage`로 렌더링. 이스케이프 담당 |

### 고치는 것

| 파일 | 변경 |
| --- | --- |
| `.../client/email/EmailDeliveryProvider.kt` | `EmailMessage(to, subject, body)` → `(to, subject, htmlBody, textBody)` |
| `.../client/email/FailoverEmailSender.kt` | 본문 조립을 `NotificationEmailTemplate`에 넘긴다. 폴백 책임만 남는다 |
| `.../client/email/SesEmailDeliveryProvider.kt` | `Body`에 `html`·`text` 둘 다 채운다 |
| `.../client/email/GmailSmtpEmailDeliveryProvider.kt` | `SimpleMailMessage` → `MimeMessageHelper(multipart)`. `MessagingException`도 전송 실패로 번역 |
| `.../client/email/EmailClientConfiguration.kt` | `NotificationEmailTemplate` 빈 등록, `emailSender`에 주입 |

### 건드리지 않는 것

- `NotificationComposer`(core-api) — 알림 문구
- `NotificationContent`, `ChannelNotificationSender` — worker 계약
- redis-core 메시지 형식, DB 스키마, Flyway
- 웹 푸시, 발송 정책, 수신 설정

## 만들 테스트

### `NotificationEmailTemplateTest` (신규, 순수 단위)

1. 제목과 본문을 HTML 본문에 담는다
2. 제목을 메일 제목으로 쓴다
3. 이동 링크를 버튼 href와 대체 주소에 담는다
4. 이동 링크가 없으면 버튼을 넣지 않는다
5. 제목과 본문의 HTML 특수문자를 이스케이프한다 — 룸 제목에 `<b>`·`&`·`"`를
   넣어 태그로 해석되지 않는 것을 확인한다
6. 본문의 줄바꿈을 `br`로 바꾼다
7. 평문 대체 본문은 본문과 링크를 빈 줄로 잇는다 (기존 형식 유지)
8. 평문 대체 본문에는 HTML 태그가 없다
9. 치환되지 않은 자리표시자가 남지 않는다 — 결과에 `{{`가 없다
   (자리표시자 이름 오타를 잡는 가드)

### 고치는 테스트

- `FailoverEmailSenderTest` — `EmailMessage` 전문 비교(`containsExactly`)를
  필드별 검증으로 바꾼다. 폴백 분기 검증은 그대로 둔다
- `SesEmailDeliveryProviderTest` — `body().html()`·`body().text()` 둘 다 확인
- `GmailSmtpEmailDeliveryProviderTest` — `multipart/alternative` 구조와
  두 파트 내용 확인, `MessagingException` 번역 확인

태그는 붙이지 않는다(기본 `test`에 포함되는 순수 단위 테스트).

## API 문서·외부 소비자 영향

- **RestDocs·OpenAPI: 없음.** HTTP API를 건드리지 않는다.
- **프론트: 없음.** 메일 생김새만 바뀐다. 링크 경로는 그대로다.
- **인프라: 없음.** SES 설정·환경 변수·IAM 권한을 바꾸지 않는다.
  HTML 본문은 기존 `ses:SendEmail` 권한으로 보낸다.
- **배포 호환성: 없음.** worker 안에서만 끝나고 메시지 형식이 안 바뀐다.

## 영향 범위

변경 파일 ~12개 + worklog 4개. PR 상한(50개) 안이다.

리스크는 **Gmail 폴백 경로**다. `SimpleMailMessage` → `MimeMessageHelper`
교체는 공급자 구현이 바뀌는 유일한 지점이고, 폴백은 SES가 죽었을 때만 타므로
운영에서 드물게 실행된다. 단위 테스트로 MIME 구조까지 확인한다.

## 단계

- [x] 체크포인트 A: 계획 승인 (2026-10-05)
- [x] 시안 승인 (2026-10-05) — 체크포인트 B보다 먼저 받았다. 사람 확인: "좋다"
- [x] 체크포인트 B: 테스트 스켈레톤(스펙) 승인 (2026-10-05)
- [x] 체크포인트 C: 구현 승인 (2026-10-05). `./gradlew test ktlintCheck` 통과,
      code-reviewer 권장 2건·제안 2건·참고 2건 반영 (decisions.md D10~D15)
- [ ] 커밋·PR (ship-pr)

체크포인트 B에서 렌더링한 HTML 예시를 Artifact로 띄워 시안을 눈으로 확인받는다.

## 남겨 두는 것 (tbd.md)

- 발신자 표시 이름(`모이면 <no-reply@...>`) — 이번 PR 범위 밖으로 둔다
- 푸터의 수신 설정 링크 — 프론트 경로를 모른다. 링크 없이 안내 문구만 넣는다
- `List-Unsubscribe` 헤더 — 광고성 메일이 생길 때 필요해진다
