# MOI-544 wiki-sync

## 구현 전 (2026-10-02)

### 확인 범위

- 이슈: [MOI-544](https://linear.app/100-thieves/issue/MOI-544) — 알림/메일 수신 설정 API·로직, 관련 PRD·정책 Wiki 추가
- 읽은 Wiki
  - `raw/meetings/slack-huddle-2026-10-01-proj-moimyeon-0q9v6d` — 수신 설정 필요성, 당시 "설정 페이지는 MVP 제외"
  - `topics/t-moimyeon-방-상태-및-알림-정책` §알림과 수신 설정
  - `raw/product/회원-및-프로필` — 수신 설정 항목 없음(갱신 전)
  - `policy/_src/` — `index.yaml`, `공통.yaml`(P.notification.*), `상태/회원.yaml`, `기능/회원-및-프로필.yaml`

### 결정 근거

[tbd.md](tbd.md) "결정됨"(2026-10-01~02 사람 확인). 설계 정리: https://claude.ai/artifact/1g6zVsci9HwFHvbigk9MBg

### 갱신 내용

| 대상 | 변경 |
| --- | --- |
| `raw/product/회원-및-프로필` | §4.10 알림 수신 설정 신설(R141~R163), §2 S2 8단계·분기(R137~R139), §3 권한(R140), §5 제외 3항목, §6 수집·비공개 데이터(R164~R166), §8 3단계, 머리말 변경 기록. `next_req` 137→167, `updated` 2026-10-02 |
| `policy/_src/상태/회원.yaml` | fact 5개(`F.member.web_push_opted_out`, `web_push_registrations`, `activity_email_enabled`, `marketing_email_agreed`, `marketing_email_agreed_at`), derived `D.member.can_receive_web_push` |
| `policy/_src/기능/회원-및-프로필.yaml` | gate `G.member.edit_notification_setting`, command 5개(사람: 설정 변경·웹 푸시 켜기·끄기 / system: 기기 등록 갱신·전달 불가 기기 삭제), transition 5개 |
| `policy/_src/공통.yaml` | `P.notification.receive_setting` |
| `policy/_src/index.yaml` | `기준_문서` "회원 및 프로필" 2026-10-02 |
| `topics/t-moimyeon-방-상태-및-알림-정책` | 수신 설정을 MVP 범위로 정정, 결정 요약, 확인 필요 항목 1개 해소 |

기존 요구 ID의 문장은 바꾸지 않았다. 새 규칙은 모두 새 ID다.

### 검증

- 쓰기 전 로컬 사본에서 `ssot_load.py assemble`·`check` 통과, `render_wiki.py` 렌더 성공
- `check_policy_refs.py` 드리프트 목록이 수정 전 기준과 동일(기존 「룸 진행 확정」·「룸 참여」 드리프트만 남음, 「회원 및 프로필」 신규 드리프트 없음)
- MCP 쓰기 후 원격 main과 로컬 검증본을 파일별로 비교해 6개 모두 일치
- 재조회: 사이드카가 조립본(`상태-SSOT.yaml`)과 `policy/정책.md`를 다시 만들었고 `P.notification.receive_setting`이 들어갔다. 원격 main에서 `ssot_load.py check` 통과
- 원격 `policy-drift-check`: "조립본 일치" 단계는 통과. "PRD 참조 검사" 단계는 실패하지만, 실패 목록은 2026-09-28부터 이어진 「룸 진행 확정」·「룸 진행 마무리 및 출석」·「룸 참여」 문장 변경 8건뿐이고 「회원 및 프로필」 항목은 없다

### 남은 불일치 (구현 전 시점)

- 코드: 수신 설정 저장·API·worker 반영이 아직 없다(구현 단계 몫). → 구현 후 대조에서 해소

## 재실행 — Boolean 이름 변경 (2026-10-02)

- 갱신: PRD R164 문구("웹 푸시를 끈 여부" → "웹 푸시 허용 여부"), 정책 문서 fact `F.member.web_push_opted_out` → `F.member.is_web_push_allowed`(값 뒤집음, 초기값 true), `F.member.activity_email_enabled`·`marketing_email_agreed` → `is_` 접두, 관련 derived·transition·policy 참조, `기준_문서` "회원 및 프로필" 2026-10-02 16:00
- 같은 시각 다른 작업(MOI-561)의 `공통.yaml`·`index.yaml` 변경을 보존한 최신본 위에서 수정했다
- 재조회: 원격 main 과 로컬 검증본 5개 파일 일치, 사이드카 재조립 뒤 `ssot_load.py check` 통과, 옛 이름 잔존 없음, 드리프트는 기존 「룸 진행 마무리 및 출석」 4건뿐

## 구현 후 대조 (2026-10-02)

대상: 회원 PRD §4.10·§3·§6(R140~R166), 상태 SSOT `P.notification.receive_setting`. 코드·테스트와 한 줄씩 대조했다.

| 요구 | 코드 | 검증 |
| --- | --- | --- |
| R140 본인 설정만 조회·변경 | `/v1/members/me/notification-setting` + `@LoginMember` | RestDocs E1102 |
| R141~R145 세 항목, 종류별 설정 없음 | `UpdateNotificationSettingRequest`·`NotificationSettingResponse` | RestDocs 조회·변경 |
| R146~R148 정책은 그대로, 받지 않는 채널만 뺌, 푸시 못 받으면 PUSH_ELSE_EMAIL 은 메일 | `ChannelNotificationSender`·`NotificationRecipient.canReceiveWebPush` | `ChannelNotificationSenderTest` 5건 |
| R149 둘 다 끄면 아무것도 안 보냄 | 위와 같음 | "웹 푸시와 메일을 모두 끈 회원에게는 아무것도 보내지 않는다" |
| R150 보내는 시점 값 | worker 가 메시지마다 `MemberNotificationRecipientFinder` 로 조회 | `MemberNotificationRecipientFinderTest` |
| R151·R153·R160 기본값(메일 켜짐, 웹 푸시 허용, 광고 꺼짐) | V32 기본값·`MemberEntity` 초기값 | `NotificationSettingServiceIT` 첫 테스트, `MySqlSchemaValidationIT` |
| R152·R155 기기마다 등록, 켜면 허용 + 이 기기 등록 | `NotificationSettingManager.change` Allow | IT "웹 푸시를 허용하면…" |
| R156 끄면 모든 기기 삭제 | Disallow → `WebPushSubscriptionManager.unregisterAll` | IT "웹 푸시 허용을 해제하면…" |
| R157 꺼진 동안 등록 무시 | `NotificationSettingManager.refreshWebPush` | IT 2건 |
| R167 꺼진 회원이 보낸 등록이 다른 회원 것이면 삭제 | `WebPushSubscriptionManager.unregisterIfOwnedByOther` | IT "웹 푸시를 허용하지 않은 회원이 다른 회원의 기기 등록을 보내면…" |
| R158 전달 실패 기기 삭제 | 기존 `DatabaseInvalidWebPushRegistrationRemover` | 기존 테스트 |
| R161 동의 시각, 철회해도 유지 | `MemberEntity.changeMarketingEmail` | IT 2건 |
| R162 동의·철회 결과 화면 안내 | 응답 `isMarketingEmailAgreed`·`marketingEmailAgreedAt` | RestDocs |
| R164~R166 저장 항목·비공개 | member 컬럼, 공개 프로필 응답에 없음 | 코드 확인 |

- 화면 몫(백엔드 대조 대상 아님): R137~R139 마이페이지 시나리오 단계(각 단계의 API 는 위 R141~R157 행이 대응), R149 "둘 다 끌 때 안내", R153 브라우저 허용 요청 시점, R154 기기 기준 토글 표시, R162 결과 표시. API 문서(`index.adoc`)에 프론트 흐름을 적었다.
- R159(광고는 메일로만)·R163(필수 안내)은 지금 해당 발송 기능이 없어 코드 대응이 없다. 명세와 어긋나는 코드도 없다.
- 불일치: 없음. Wiki 갱신 필요 없음.

## 재실행 — QA 리뷰 반영 (2026-10-02)

- 계기: qa-reviewer 필수 1 — 웹 푸시를 끈 회원이 같은 브라우저를 쓰던 다른 회원의 등록을 보내면 그 등록이 남아 앞사람 알림이 계속 뜬다. 사용자 확인 후 반영.
- 갱신: PRD §4.10 R167 신설(`next_req` 167→168), `기능/회원-및-프로필.yaml` 의 `C.member.refresh_web_push` 출처·설명과 `T.member.refresh_web_push` 쓰기 한 줄 추가, `index.yaml` `기준_문서` "회원 및 프로필" 2026-10-02 17:00
- 재조회: 원격 main 과 로컬 검증본 3개 파일 일치, 사이드카 재조립 뒤 `ssot_load.py check` 통과(조각 21개), 조립본에 R167 반영, 드리프트는 기존 「룸 진행 마무리 및 출석」 4건뿐
- 코드 대응: 위 구현 후 대조 표의 R167 행
