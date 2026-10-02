# MOI-540 wiki-sync (구현 전)

## 확인 범위

- PRD 「회원 및 프로필」 §2·§4.8·§4.9·§6·§8, 다른 PRD의 탈퇴 표시 문구(「룸 진행」 R55·R85)
- 상태 SSOT `_src`: `상태/회원.yaml`, `기능/회원-및-프로필.yaml`, `공통.yaml`, `결정.yaml`, `index.yaml`
- 요약: `sources/prd-회원-및-프로필-요약`

## 결정 근거

decisions.md 1~16 (2026-10-02 사람 결정). 상태 모델(탈퇴 = 탈퇴 시각에서 파생)은 SSOT DEC-013.

## 갱신 내용

- 「회원 및 프로필」: R89·R94·R96·R97·R128·R92·R68 개정, 새 줄 R168(§2 복구 분기),
  R169~R173(탈퇴 처리), R174~R179(탈퇴 계정 복구), 2026-10-02 변경 콜아웃, §8 3단계에 복구
- 「룸 진행」: R55·R85 표시 문구 "탈퇴한 회원"
- SSOT: `F.member.account_status`에서 WITHDRAWN 제거, `D.member.withdrawn` 신설, 게이트 4곳 교체,
  `G/C/T.member.restore` 신설, `T.member.withdraw`·`X.withdraw.leave_active_rooms` 정리,
  `P.member.withdrawal`·`P.member.restriction`·`P.participation.exit`·`P.member.identity`·`G.member.signup` 정리,
  DEC-013(상태 모델)·DEC-014(D39 해소)·DEC-015(복구 확인 10분)
- 후속 정리: `G.member.restore` 열린 질문 해소(10분), `F.member.account_status` 설명을
  "탈퇴 후 액세스 토큰이 남아도 백엔드가 탈퇴 회원을 빼고 조회해 회원 없음으로 실패"로 정정

- 구현 중 결정 반영(2026-10-03): DEC-016(탈퇴 처리 단위 — 신청 철회 → 룸마다 나가기 → 회원 정리),
  `X.withdraw.leave_active_rooms` note·대기 신청 철회 detail, `T.member.withdraw` note, `P.member.withdrawal` source

- QA 반영(2026-10-03): DEC-017(복구 확인 정보는 발급 뒤 재탈퇴 시 무효), `G.member.restore#confirmed-in-window` ref·note.
  파트 파일 재조회 일치, 렌더링된 결정 로그에 DEC-017 표시 확인(로컬 ssot_load check 는 같은 이유로 못 돌림).

## 재조회 결과

- (2차, DEC-016 반영분) 세 파트 파일을 다시 읽어 쓴 내용과 일치 확인, 렌더링된 결정 로그에 DEC-016 표시 확인.
  로컬 ssot_load check 는 이 반영분에는 돌리지 못했다(로컬 Wiki 클론 접근이 권한 설정으로 막힘). 아래 항목은 1차 반영분 결과다.

- `ssot_load check` 통과, `render_ssot --check` 오류 수 변화 없음(27/104/158), PRD id 중복 없음, 인용 id 실재
- 남은 기존 문제(이번 범위 밖): DEC-007~012가 `결정.yaml`이 아니라 `공통.yaml`에 있어 렌더 치명 오류 대부분을 차지

## 코드·테스트 대응 (구현 후 대조)

| 명세 | 코드 | 테스트 |
| --- | --- | --- |
| R169 탈퇴 요청 한 번에 처리(신청 철회 → 룸마다 나가기 → 회원 정리, 단계별 커밋) | `MemberService.withdraw`, `MemberWithdrawer.withdraw` | `MemberServiceTest`(순서), `MemberWithdrawIT`, `MemberWithdrawRollbackIT`(회원 정리 커밋 롤백) |
| R96·R97 활성 룸 나가기(방장 위임·모집 재개·취소) | `RoomLeaveManager.leaveOnWithdrawal`(룸마다 커밋) | `MemberWithdrawIT` 방장·참여자·혼자 방장 |
| R170 예정 시각 지난 확정 룸 남김, 완료·취소 룸 그대로 | 같은 메서드의 판정 | `RoomLeaveManagerTest` 경계, `MemberWithdrawIT` |
| R171 대기 신청 철회 | `withdrawAllPending` | `MemberWithdrawIT` |
| R172 모든 세션 종료·웹 푸시 등록 삭제 | `SessionManager.closeAll`, `WebPushSubscriptionManager.unregisterAll` | `MemberWithdrawerTest`, `MemberWithdrawIT` |
| G.member.withdraw 멱등 | 탈퇴 회원이면 바로 반환 | `MemberWithdrawerTest` |
| R128·R173 "탈퇴한 회원" 표시 | `RoomApplicationFacade`, `RoundFeedbackReader` 문구 | 해당 테스트 기대값 |
| R168·R174 탈퇴 회원 로그인 → 복구 확인 | `SocialAuthService`, `OAuth2LoginSuccessHandler`, `RestoreTokenProvider` | `SocialAuthServiceTest`, `OAuth2LoginSuccessHandlerTest`, `MemberWithdrawIT` |
| R175 확인 시 복구·로그인, DEC-015 10분, DEC-017 재탈퇴 뒤 토큰 무효 | `POST /v1/auth/restoration`, `MemberRestorer` | `AuthControllerTest`, `RestoreTokenProviderTest`(만료), `MemberRestorerTest`(재탈퇴), `RestorationSecurityContextTest`(필터 체인) |
| R176·R177 복구 범위 | `MemberRestorer`(탈퇴 시각만 해제) | `MemberWithdrawIT` 나간 룸 유지 |
| R178 이용 제한 유지 | 상태값 분리(DEC-013) | `MemberRestorerTest` |

## 남은 불일치

- 없음. `C.member.restore` 멱등(예)에 맞춰 이미 복구된 회원의 재요청도 로그인시킨다(처음 구현의 E1014 제거).
