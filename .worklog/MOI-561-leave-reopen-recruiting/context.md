# MOI-561 컨텍스트

Linear: https://linear.app/100-thieves/issue/MOI-561 (MOI-540을 막는 이슈, 스택 PR의 아래층)

확정된 룸에서 방장은 인원과 관계없이 나갈 수 있고 나가면 위임 후 모집 중으로 돌아간다(MOI-541).
일반 참여자는 현재 인원이 최소 진행 인원과 같으면 나갈 수 없다(MOI-397, E1423).
처음에는 둘 다 막았지만 MOI-541에서 방장만 풀려 비대칭이 남았다.
이 차단은 회원 탈퇴의 "활성 룸이 있어도 즉시 탈퇴 허용"(「회원 및 프로필」 §4.8 R95)과도 충돌한다.

## 확정된 요구사항 (2026-10-02 사람 결정)

- 확정된 룸의 참여자는 현재 인원과 관계없이 나갈 수 있다.
- 나간 뒤 인원이 최소 진행 인원보다 적어지면 룸을 모집 중으로 되돌린다. 방장 이탈 때의 모집 재개와 같은 방식이다.
- 최소 진행 인원 이상이 남으면 확정 상태를 유지한다.
- 참여자 이탈로 모집이 재개되면 방장과 남은 참여자에게 알린다.
- 방장 이탈(위임·모집 재개·취소)은 바꾸지 않는다.

## 근거 문서 (team-wiki)

- 「룸 참여 및 참여자 관리」 §4.6 (20260811 추가 문단: 최소 인원 밑으로 떨어지는 이탈 차단)
- 「룸 진행 확정」 §4.3
- 상태 SSOT `C.participation.cancel` 게이트(최소 인원 조건 없음), 룸 상태 전이
- `topics/t-moimyeon-방-상태-및-알림-정책` (알림 대상)
- 결정 출처: MOI-397 이슈 「결정 필요」 2번(2026-08-11 채택), MOI-541 DR-002

## 관련 코드 위치

| 위치 | 내용 |
| --- | --- |
| `core/core-api/.../core/domain/room/RoomLeaveManager.kt` | 나가기 규칙. `requireAboveMinCapacity`(참여자만), `delegateOrCancel`(방장 이탈 시 모집 재개 + 상태 이력 + 위임 이벤트) |
| `core/core-api/.../core/domain/participation/RoomParticipantService.kt:35` | `leave` → `RoomLeaveManager.leave` 한 줄 |
| `core/core-api/.../core/domain/room/RoomManager.kt:202` | 재확정. `RoomConfirmation`이 최소 인원·일정·이전 확정 이력을 판정 |
| `storage/db-core/.../RoomEntity.kt:93` | `reopenRecruiting()` |
| `core/core-api/.../core/support/error/CoreErrorType.kt:83`, `ErrorCode.kt:58` | `ROOM_AT_MIN_CAPACITY` / `E1423` |
| `core/core-enum/.../EventType.kt`, `core/core-api/.../event/EventPayloadClass.kt`, `event/payload/` | outbox 이벤트 종류와 payload 짝 |
| `core/core-api/.../core/notification/NotificationComposer.kt:104` | 위임 알림 문구(방장·참여자별) |
| `core/core-api/src/test/.../domain/room/RoomLeaveManagerTest.kt` | 나가기 규칙 단위 테스트 (E1423 테스트 포함) |
| `core/core-api/src/test/.../domain/room/RoomLeaveIT.kt` | 위임·모집 재개·이벤트 발행 IT ("방장이 아닌 참여자가 나가면 사실을 발행하지 않는다" 포함) |
| `core/core-api/src/test/.../api/controller/v1/RoomParticipantControllerTest.kt:198` | `roomLeaveAtMinCapacity` RestDocs, `leaveDescription` 문구 |
| `NotificationComposerTest`, `OutboxEventSerializerTest` | 새 이벤트를 넣으면 함께 갱신 |

## 작업 경계

- 회원 탈퇴(MOI-540)는 이 위에 스택으로 올린다.
- 방장 이탈 동작은 바꾸지 않는다.
- 프론트의 참여 취소 버튼 비활성 처리 제거는 프론트 몫이다(공유만).
