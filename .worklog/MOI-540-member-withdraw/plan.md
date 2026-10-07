# MOI-540 구현 계획

스택: dev ← MOI-561(#146) ← MOI-540. 결정은 decisions.md, 미결은 tbd.md.

## 단계

- [x] A. 계획 승인 (2026-10-02, ①~④ 추천안 채택, 탈퇴 시 자동 처리 + 사전 안내, 예정 시각 지난 룸은 남김)
- [x] B. 명세 동기화(wiki-sync) + 테스트 스켈레톤 승인 (2026-10-02)
- [x] C. 구현 승인 (2026-10-03) (리뷰·`./gradlew test ktlintCheck` 통과)
  - 리뷰: db-reviewer 필수 1·권장 2, code-reviewer 필수 1(같은 건)·권장 5 반영. `./gradlew test ktlintCheck`·`restDocsTest` 통과
  - 변경 파일 50개 초과로 PR을 둘로 나눈다: 탈퇴(이 브랜치) → 복구(위에 스택)
- [ ] D. 커밋·PR (base: MOI-561 브랜치)

## 접근

### 탈퇴

한 트랜잭션으로 묶는다. 잠금 순서는 저장소 관례(회원 → 룸)를 따른다.

1. 회원 행 잠금 (이미 탈퇴면 거절)
2. 참여 중인 활성 룸(모집 중·확정)에서 차례로 나가기: 기존 `RoomLeaveManager.leave` 재사용
   - 단, 진행 예정 시각이 지난 확정 룸은 나가지 않는다(결정 11)
   - 방장이면 위임·모집 재개·취소, 참여자면 MOI-561 규칙(최소 미만이면 모집 재개)
   - 완료·취소된 룸은 건드리지 않는다 (활동 기록 보존, R128)
3. 대기 중인 참가 신청 일괄 철회 (기존 `MemberWithdrawer`에 있음)
4. 리프레시 토큰 전부 폐기 (회원 단위 폐기 메서드 추가)
5. 웹 푸시 구독 삭제 [결정 필요 ②]
6. 회원 소프트 삭제 (`deleted_at`)
7. 응답에서 액세스·리프레시 쿠키 만료 (로그아웃과 같은 방식)

API: `DELETE /v1/members/me` [결정 필요 ①]

### 복구 (재로그인 + 확인 단계)

지금은 탈퇴한 Google 계정으로 로그인하면 재가입 차단(E1001)에 걸려 `?authError=login_failed`로만 돌아간다.
확인 단계를 이어 갈 장치(임시 토큰 등)는 저장소에 없어 새로 만든다. [결정 필요 ③]

제안 흐름:
1. 로그인 성공 처리에서 탈퇴한 회원을 찾으면 세션을 열지 않는다.
2. 짧게 사는 복구 확인용 쿠키(서명된 토큰, 회원 id·만료 10분, HttpOnly, path `/v1/auth`)를 심고
   프론트의 복구 확인 화면(예: `{front}/auth/restore`)으로 보낸다.
3. 사용자가 복구를 누르면 `POST /v1/auth/restoration` → 쿠키 검증 → 회원 `deleted_at` 해제 →
   세션 발급(기존 로그인과 같은 쿠키) → 복구 확인 쿠키 만료.
4. 취소하면 아무것도 바뀌지 않는다(쿠키는 만료).

복구 범위(결정 6): 계정·프로필·이력서만 돌아온다. 나간 룸·철회된 신청·폐기된 세션은 되돌리지 않는다.
이용 제한(RESTRICTED) 상태는 탈퇴와 별개 값이라 복구 뒤에도 그대로다(결정 8).
회원 행이 없는 경우(후속 MOI-562의 마스킹 이후)의 새 가입 처리는 MOI-562에서 다룬다.

### 표시 문구

탈퇴한 작성자 표시를 "탈퇴한 회원"으로 통일한다: `RoomApplicationFacade.kt:107`("탈퇴한 사용자"),
`RoundFeedbackReader.kt:85`("탈퇴 회원") 두 곳.

## [결정 필요]

① **탈퇴 API 경로.** `DELETE /v1/members/me`를 추천한다. 저장소 관례가 "내 것을 없앤다 = DELETE"
   (`DELETE .../participants/me`, `DELETE .../applications/me`)이고, 회원 입장에서 탈퇴는 내 계정을 없애는 행위다.
   대안 `POST /v1/members/me/withdrawal`(상태 전이 = POST + 명사)은 복구가 가능하다는 점을 더 잘 드러낸다.
② **웹 푸시 구독.** 탈퇴 시 삭제를 추천한다. 탈퇴 회원에게는 이미 알림이 안 가므로 남길 이유가 없고,
   기기 정보라 지우는 편이 깔끔하다. 복구하면 다시 구독해야 한다.
③ **복구 확인 방식.** 위 제안(복구 확인 쿠키 + 프론트 확인 화면 + `POST /v1/auth/restoration`)을 추천한다.
   프론트 작업(확인 화면, 리다이렉트 경로)이 필요하다. 보안 모듈의 로그인 성공 처리를 고친다.
④ **탈퇴 직후 액세스 토큰.** 쿠키는 응답에서 만료시키고 리프레시는 막히지만, 이미 발급된 액세스 토큰(최대 30분)을
   Bearer로 쓰면 그 사이 요청이 통과한다. 요청마다 회원을 조회해 막는 것은 모든 API에 DB 조회를 더한다.
   추천: 이번에는 허용하고 쿠키 만료로 막는다. 웹 클라이언트는 쿠키만 쓴다.

## 변경 지점 (예상)

| 영역 | 변경 |
| --- | --- |
| `MemberController`/`MemberFacade`/`MemberService` | 탈퇴 API |
| `MemberWithdrawer` | 활성 룸 나가기·세션 폐기·푸시 구독 삭제 추가 |
| `RefreshTokenRepository`, `WebPushSubscriptionRepository` | 회원 단위 폐기·삭제 쿼리 |
| `ParticipationRepository`/`Finder` | 회원의 활성 룸 참여 목록 |
| security-core 로그인 성공 처리, 포트 | 탈퇴 회원 분기, 복구 확인 쿠키·리다이렉트 |
| `AuthController` + 복구 Service/Implement | `POST /v1/auth/restoration` |
| `MemberRegistrar`/`SocialAuthService` | 탈퇴 회원을 재가입 차단 대신 복구 대상으로 판정 |
| `AuthCookieFactory`, `security-core.yml` | 복구 확인 쿠키, 복구 화면 리다이렉트 주소 |
| 표시 문구 2곳 | "탈퇴한 회원" |
| RestDocs·`index.adoc` | 탈퇴·복구 API 문서 |

## 테스트 (스켈레톤 단계에서 구체화)

- `MemberWithdrawer` 단위: 회원 없음·이미 탈퇴 거절, 각 단계 호출
- IT(바깥 트랜잭션 없음): 방장·참여자로 있는 확정·모집 룸에서 탈퇴 → 위임·모집 재개·나가기 결과,
  대기 신청 철회, 세션 폐기, 회원 소프트 삭제가 한 커밋. 완료된 룸 기록은 그대로
- 복구: 탈퇴 회원 로그인 시 세션 없이 복구 확인으로 이동, 복구 후 로그인 상태·제한 상태 유지, 만료·위조 쿠키 거절
- RestDocs: 탈퇴 성공·이미 탈퇴, 복구 성공·쿠키 없음/만료

## 명세 동기화 (wiki-sync, 구현 전)

- PRD 「회원 및 프로필」 §4.8·§4.9·§6: R89(로그인 제한 → 확인 후 복구), 복구 범위, 활성 룸 처리(R95–R97을
  「룸 참여」 §4.6·R36에 연결, R97의 노쇼 서술 정리), R128 표시 문구, 재가입 규칙, 세션·푸시 처리
- 상태 SSOT: `account_status`에서 WITHDRAWN 제거 → 탈퇴 시각 파생, `C.member.login` 게이트, 새 복구 command,
  `C.member.withdraw` 전이, `X.withdraw.leave_active_rooms`·`P.member.withdrawal`·`P.participation.exit` 낡은 서술,
  `G.member.signup` 열린 질문 해소, 결정 로그 D39 정리
- 다른 PRD의 "탈퇴한 사용자" 문구

## 영향

- 프론트: 탈퇴 버튼·안내, 복구 확인 화면과 리다이렉트 경로, 탈퇴 응답 후 로그아웃 상태 처리
- 로그인 흐름이 바뀐다(보안 모듈). 기존 회원 로그인은 동작 그대로
