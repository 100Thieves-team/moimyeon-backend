# MOI-540 테스트 스켈레톤

근거: decisions.md 1~16, PRD 「회원 및 프로필」 §4.8 — 탈퇴 R94·R95·R96·R97·R169~R173·R128, 복구 R168·R174~R179.

## 탈퇴

### MemberWithdrawerTest (단위 — 규칙)

```kotlin
@Test fun `탈퇴한 회원이 다시 탈퇴하면 E1006 을 던진다`()          // 기존 테스트 유지(이미 탈퇴 = 없는 회원)
@Test fun `탈퇴하면 회원이 소프트 삭제되고 대기 신청이 철회된다`()     // 기존 유지
@Test fun `탈퇴하면 그 회원의 세션을 모두 끝내고 웹 푸시 구독을 지운다`()          // R172
@Test fun `탈퇴하면 모집 중이거나 확정된 룸에서 차례로 나간다`()
    // given 참여 목록: 모집 중 룸 A, 확정 룸 B(예정 시각 미래)
    // then roomLeaveManager.leave(A), leave(B) 호출
@Test fun `진행 예정 시각이 지난 확정 룸에서는 탈퇴해도 나가지 않는다`()          // R170
    // given 확정 룸 C(예정 시각 == 지금, 경계: 지난 것으로 본다)
    // then leave(C) 호출 없음
@Test fun `완료되거나 취소된 룸에서는 탈퇴해도 나가지 않는다`()
```

### MemberWithdrawIT (통합 — 한 커밋, 바깥 트랜잭션 없음)

```kotlin
@Test fun `확정 룸의 방장이 탈퇴하면 다음 참여자에게 방장이 넘어가고 룸은 모집 중으로 돌아간다`()
@Test fun `확정 룸의 참여자가 탈퇴해 최소 인원보다 적어지면 룸이 모집 중으로 돌아간다`()   // MOI-561 규칙이 탈퇴에서도 동작
@Test fun `혼자 있는 방장이 탈퇴하면 룸이 취소된다`()
@Test fun `진행 예정 시각이 지난 확정 룸의 참여자로 탈퇴하면 확정 참여자로 남는다`()
@Test fun `탈퇴하면 대기 신청 철회·세션 폐기·회원 소프트 삭제가 한 번에 반영된다`()
    // then 리프레시 토큰 전부 revoked, 참가 신청 WITHDRAWN, member.deleted_at != null
@Test fun `완료된 룸의 참여·출석·후기 기록은 탈퇴 후에도 그대로다`()
```

### MemberControllerTest (RestDocs) — `DELETE /v1/members/me`

```kotlin
fun withdraw()                  // 200, 액세스·리프레시 쿠키 만료(Set-Cookie Max-Age=0)
fun withdrawAlreadyWithdrawn()  // E1006
```

## 복구

### SocialAuthServiceTest (단위 — 흐름)

```kotlin
@Test fun `탈퇴한 회원의 신원이면 가입하지 않고 복구 확인 대상으로 돌려준다`()
@Test fun `기존 회원·처음 보는 신원 처리는 그대로다`()          // 기존 두 테스트 유지
```

### OAuth2LoginSuccessHandlerTest (security-core 단위)

```kotlin
@Test fun `탈퇴한 회원이면 세션 쿠키 없이 복구 확인 쿠키를 심고 복구 확인 화면으로 이동한다`()
    // then ACCESS/REFRESH 쿠키 없음, RESTORE 쿠키(HttpOnly, path /v1/auth, 10분), redirect = 복구 확인 주소
@Test fun `일반 회원 로그인은 지금처럼 세션 쿠키를 발급하고 콜백으로 이동한다`()   // 기존 유지
```

### 복구 확인 토큰 (security-core 단위)

```kotlin
@Test fun `복구 확인 토큰은 발급한 회원 id 를 되돌려 준다`()
@Test fun `만료되거나 서명이 다른 복구 확인 토큰은 거절한다`()
@Test fun `복구 확인 토큰을 액세스 토큰으로 쓰면 인증되지 않는다`()   // 같은 서명 키를 쓰므로 용도 구분을 고정
```

### MemberRestorerTest / IT

```kotlin
@Test fun `탈퇴한 회원을 복구하면 탈퇴 시각이 지워지고 다시 로그인할 수 있다`()
@Test fun `복구해도 이용 제한 상태는 그대로다`()                    // R178
@Test fun `탈퇴하지 않은 회원을 복구하려 하면 거절한다`()          // 새 에러 코드 [결정: E10xx 번호대에서 다음 빈 번호]
@Test fun `복구해도 나간 룸과 철회된 신청은 돌아오지 않는다`()     // IT, R177
```

### AuthControllerTest (RestDocs) — `POST /v1/auth/restoration`

```kotlin
fun restore()                    // 200, 세션 쿠키 발급, 복구 확인 쿠키 만료
fun restoreWithoutToken()        // 복구 확인 쿠키 없음·만료 → 401 계열 에러 코드
fun restoreNotWithdrawn()        // 탈퇴 상태가 아님
```

## 표시 문구

```kotlin
// RoomApplicationFacade·RoundFeedbackReader 를 쓰는 기존 테스트의 기대 문구를 "탈퇴한 회원"으로 맞춘다
```
