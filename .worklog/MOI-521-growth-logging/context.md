# MOI-521 컨텍스트

[MOI-521: 그로스 해킹용 로깅 추가](https://linear.app/100-thieves/issue/MOI-521)는 (1) 백엔드에서 관측할 지표를 정하고
(2) 그 로그를 기록·관측하는 방법을 검토하는 작업이다. 상태 Todo, 댓글·첨부·연결 이슈 없음. 본문이 두 줄이라
사용자에게 범위를 확인했다(2026-10-05).

- 지표: 코드베이스와 LLM Wiki 기획을 근거로 추천안을 만든다. 결정은 팀이 한다.
- 관측 도구: 종류 무관. AI로 쉽게 묻고 시각화가 쉬운 것을 선호한다.
- 사용자 식별 방식: 에이전트 판단에 맡김 → `recommendation.md` 4절.

## 근거 문서

- Wiki PRD 성공 지표(§8 "퍼널 이벤트 수집"): `raw/product/룸-탐색`, `회원-및-프로필`, `룸-생성`,
  `룸-참여-및-참여자-관리`, `룸-진행-확정`, `룸-진행-마무리-및-출석`, `유저-후기-및-신뢰-관리`
- 상태 흐름: `policy/상태-흐름`
- FE/BE 분담: `raw/meetings/slack-huddle-2026-09-08-proj-moimyeon-syqeer` — 클라이언트는 화면 전환·클릭, 서버는 비즈니스 흐름
- 그로스 SaaS 보류: `sources/s-2026-09-09-proj-moimyeon-slack-huddle` — Mixpanel·Amplitude·AirBridge 후보, 결정은 멘토링 후
- 확정 상태 이후 트래킹 연동 예정: `sources/s-2026-09-27-proj-moimyeon-허들`
- 로그 경로 계약: `topics/t-moimyeon-로깅-아키텍처와-사용-가이드`, `raw/technical/architecture/moimyeon-backend-observability-2026-09-11`
- 저장소 내 선행 결정: `.worklog/MOI-411-logging-config/decisions.md` DR-12(확정 사건부터, SID·UTM 후속), DR-21(그로스 로그는 DB와 대조하는 분석 자료)

## 관련 코드

- `infra/terraform/modules/application-logging/router/v1/sanitize.lua` — `category=growth`·INFO만 growth로 분기. `:2` 허용 필드 목록 외 필드는 버림
- `infra/terraform/modules/application-logging/router/v1/fluent-bit.conf.tftpl` — growth → S3 `retention=growth/` 90일
- `support/logging/.../LogSanitizer.kt` — `category`·`eventCode` 예약, 임의 eventCode 불가
- `support/logging/.../RequestLogEntry.kt`, `RequestLogWriter.kt` — 타입 있는 구조화 로그의 선례
- `core/core-api/.../core/event/OutboxEventPublisher.kt`, `event/outbox/OutboxRelay.kt` — 쓰기 트랜잭션 내 발행, AFTER_COMMIT 비동기 전달, 실패 시 전 소비자 재전달
- `core/core-api/.../core/event/payload/*` — outbox 이벤트 10종(신청 제출·수락·반려, 룸 확정·완료·취소·방장 위임·모집 재개, 후기 공개, 방명록)
- 트랜잭션 경계는 Service가 아니라 Manager/Registrar/Recorder에 있다
- 사건 위치: `domain/member/MemberRegistrar.kt:23`(가입), `SocialAuthService.kt:18,24`(가입·재로그인 분기),
  `MemberWithdrawer.kt:30`, `MemberRestorer.kt:23`, `domain/room/RoomManager.kt:70`(생성), `:201`(확정),
  `domain/roomapplication/RoomApplicationSubmissionManager.kt:44`(신청)·`:80`(철회),
  `domain/room/RoomApplicationManager.kt:47`(수락)·`:103`(반려), `domain/progress/RoomProgressManager.kt:37,78`(완료),
  `domain/room/RoomLeaveManager.kt:49,101,171`, `domain/trust/ReviewSubmissionManager.kt:31`, `ReviewSkipRecorder.kt:17`,
  `domain/resume/ResumeRegistrar.kt:39`
- UTM·referrer·초대·익명 방문자 ID 처리는 저장소에 없다. MDC에 memberId 없음. 비즈니스 Micrometer 카운터 없음.

## 작업 경계

- 이번 단계는 지표·이벤트·도구 추천안까지다. 코드·인프라 변경은 팀 결정 후 별도 단계.
- 화면 조회·클릭·"시작" 류 이벤트(상세 조회, 신청 시작 등)는 FE 범위로 둔다.
- 결정이 나면 Wiki(관측성·로깅 가이드 topic)에 반영하는 것은 `wiki-sync`로 같은 작업 안에서 처리한다.
