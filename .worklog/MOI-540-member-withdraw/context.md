# MOI-540 컨텍스트

Linear: https://linear.app/100-thieves/issue/MOI-540/회원-탈퇴-api-및-비즈니스-로직-구현

실서비스에 필요한 회원 탈퇴 기능이 없다. 이슈가 요구하는 결과물은 세 가지다.
(1) 정해지지 않은 정책·스펙을 먼저 정한다. (2) 비즈니스 로직과 API를 연결해 PR을 올린다.
(3) 정한 정책을 LLM Wiki PRD/스펙에 반영한다. 기존 문서를 고칠지 새로 만들지는 작업자가 판단한다.
2026-10-01 허들에서 회원 탈퇴 API 구현이 후속 작업으로 정해졌다
(team-wiki `sources/s-2026-10-01-slack-huddle-proj-moimyeon`).

## 근거 문서 (team-wiki)

- PRD 원본: `raw/product/회원-및-프로필` §4.8 회원 탈퇴, §4.9 엣지 케이스, §6 데이터
- 상태 SSOT: `policy/command-게이트`의 `C.member.withdraw`, `C.member.mask_pii`, `C.resume.purge`, `C.member.login`
- 상태 SSOT: `policy/상태-흐름`의 `ACTIVE/RESTRICTED → WITHDRAWN`
- 미결: `policy/결정-로그`의 D39(탈퇴 시 후기·통계·제출 이력서 처리 근거 없음), D24(시스템 처리 실행 방식)
- 열린 질문: `F.member.oauth_provider_id`·`G.member.signup`(재가입), `F.member.nickname`(마스킹 후 중복 점유),
  `F.member.job`(마스킹 대상 누락), `F.resume.purged_at`(소프트/하드, 7일 기산점)

## 요구사항 핵심 (PRD §4.8 발췌)

- 사용자는 탈퇴를 요청할 수 있다 `R86`. 탈퇴 전 안내 항목이 있다 `R87–R91`. 안내 문구는 프론트 몫이다.
- 탈퇴 후 데이터 처리
  - 이력서(파일·원본·AI 요약)는 7일 이내 **삭제** `R126`
  - 개인정보(이메일·OAuth 식별자·닉네임·자기소개·관심 정보)는 30일 이내 **마스킹** `R127`
  - 활동 기록(룸 참여·출석·후기·질문·기록)은 보존하고 작성자를 "탈퇴한 사용자"로 표시 `R128`
  - 원본 공개 룸의 이력서는 "탈퇴로 삭제된 이력서"로 안내 `R93`
- 활성 룸이 있어도 **즉시 탈퇴를 허용**한다 `R95`
  - 방장이면 "방장 나가기"(자동 승계) 로직이 동작한다 `R96`
  - 진행 중 룸은 진행 현황에 따라 나가기 또는 노쇼로 처리한다 `R97`
- 게이트(`G.member.withdraw`): 본인 계정일 것, 이미 탈퇴한 계정이 아닐 것. 멱등이다.
- 로그인 게이트: 탈퇴 계정은 로그인 불가 ("탈퇴한 계정이라 로그인할 수 없어요")

## 관련 코드 위치

| 위치 | 내용 |
| --- | --- |
| `core/core-api/.../core/domain/member/MemberWithdrawer.kt` | 탈퇴 Implement가 이미 있다(MOI-499). 회원 잠금 → 대기 신청 일괄 철회 → `member.delete(now)`. 호출하는 곳은 없다 |
| `.../core/domain/member/MemberRegistrar.kt:33` | 탈퇴한 Google 계정은 재가입 시 `MEMBER_ALREADY_WITHDRAWN`(409 E1001) |
| `.../core/domain/session/SessionAuthenticator.kt` | 리프레시 시 탈퇴 회원이면 `INVALID_SESSION`. 액세스 토큰(JWT 30분)은 서버에서 무효화할 수 없다 |
| `.../core/domain/session/SessionManager` | `refresh_token` 저장. 회원 단위 일괄 폐기 메서드는 없다 |
| `.../core/api/controller/v1/AuthController.kt:39` | 로그아웃. 쿠키 만료(`AuthCookieFactory.expireAccess/expireRefresh`) 재사용 가능 |
| `.../core/api/security/LoginMemberArgumentResolver.kt` | `@LoginMember currentMember: CurrentMember`로 인증 주체 주입 |
| `.../core/domain/room/RoomLeaveManager.kt:49` | 룸 나가기. 방장이면 참여자 → 대기 신청자 → 취소 순으로 위임. 확정 룸의 일반 참여자는 최소 인원 이하면 `ROOM_AT_MIN_CAPACITY`로 막힌다 |
| `core/core-enum/.../MemberStatus.kt` | `ACTIVE`, `RESTRICTED`만 있다. 탈퇴는 `deleted_at`으로 표현한다 (`docs/conventions/storage.md:83-85`) |
| `storage/db-core/src/main/resources/schema.sql:289,303` | `member`(nickname 유니크), `social_account`(provider+provider_id 유니크, 재가입 차단용으로 탈퇴 후에도 유지) |
| `storage/object-storage/.../S3ResumeFileStore.kt` | `store`·`read`·`issueViewUrl`만 있고 삭제 메서드가 없다 |
| `core/core-worker/.../MemberNotificationRecipientFinder` | 탈퇴 회원은 알림 대상에서 이미 빠진다 |
| `ReviewFacade`, `RoomApplicationFacade`, `RoundFeedbackReader` | `MemberFinder.getAttributionsIncludingWithdrawn`으로 탈퇴 작성자를 "탈퇴한 회원"/"탈퇴 회원"으로 표시 중 (문구 두 가지 혼재) |
| `core/qa/QaMemberResetter`, `QaMemberEraser` | 회원에 연결된 테이블 전체 목록으로 참고할 만하다 |
| `core/core-batch` | 예제 잡만 있다. 탈퇴 후 마스킹·이력서 삭제를 맡을 정기 작업은 없다 |
| `core/core-api/src/test/.../RoomParticipantControllerTest.kt:173` | 비슷한 기능의 RestDocs 테스트 예시(룸 나가기) |

회원을 참조하는 테이블(FK 제약 없음): `member_profile`, `member_profile_interest_*`, `resume`, `terms_agreement`,
`room_application`, `participation`, `resume_submission`, `question`, `answer_summary`, `question_comment`,
`attendance`, `review`, `review_skip`, `guestbook_post`, `room_status_log`, `refresh_token`, `web_push_subscription`, `outbox`.

## 작업 경계 (하지 않는 것)

- 탈퇴 전 안내 화면과 문구 (프론트)
- Google OAuth 연동 해제 (외부 연동이 없고 PRD도 요구하지 않음)
- 계정 이용 제한(RESTRICTED) 정책 (D45)
- 알림·이메일 수신 설정 (10/01 허들에서 MVP 제외)
- 범위 판단이 필요한 항목은 `tbd.md` 참고: 7일·30일 후속 처리(정기 작업)를 이번 PR에 넣을지
