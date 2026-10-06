# MOI-571 테스트 스켈레톤

근거: 「룸 참여 및 참여자 관리」 R185, 「룸 진행 마무리 및 출석」 R76·R28 (`wiki-sync.md`).
결정(닉네임 대체 문구·정렬)은 `decisions.md`.

## RoomParticipantReaderIT (통합 — 확정 명단 조회)

기존 픽스처(`persistRoom`, `joinWithResume`, `persistParticipation`, `recordConfirmation`)를 쓴다.
`persistParticipation`에 `leftAt`을 받을 수 있게 하고, `recordConfirmation`에 확정 시각을 받을 수 있게 한다.

```kotlin
// R185 · R76 — 확정 상태로 남은 룸의 확정 후 이탈자는 출석 대상이다
@Test fun `확정 룸의 확정 명단에는 확정 후 나간 참여자도 남는다`()
    // given CONFIRMED, 방장·참여자 확정 전 합류, 확정 로그, 참여자 LEFT(leftAt = 확정 이후)
    // when getConfirmedByRoom(roomId)
    // then memberId 목록 == [방장, 나간 참여자] (확정 당시 참여 순서), 현재 명부(getAllByRoom)에는 방장만

// R28 — 경계의 반대쪽
@Test fun `확정 전에 나간 참여자는 확정 명단에 없다`()
    // given CONFIRMED, 참여자 LEFT(leftAt = 확정 이전), 확정 로그
    // then 확정 명단 == [방장]

// R185 — 다른 상태에서는 비어 있다
@Test fun `모집 중인 룸의 확정 명단은 비어 있다`()
    // given RECRUITING, 참여자 2명
    // then 빈 목록

@Test fun `취소된 룸의 확정 명단은 비어 있다`()
    // given CANCELED, 확정 로그가 있었던 룸
    // then 빈 목록

@Test fun `완료된 룸은 확정 당시 명단을 돌려준다`()
    // given COMPLETED, 확정 로그, 확정 후 이탈자 1명
    // then 확정 명단 == [방장, 이탈자]

// R76 — 재확정은 마지막 확정 시점 기준
@Test fun `확정이 풀렸다가 다시 확정된 룸은 마지막 확정 시점의 명단을 돌려준다`()
    // given 1차 확정(참여자 A 포함) → A 이탈 → 참여자 B 합류 → 2차 확정 로그, 상태 CONFIRMED
    // then 확정 명단 == [방장, B] (A 없음)

// 결정 — 탈퇴로 회원 행이 사라져도 명단은 성립한다
@Test fun `탈퇴한 확정 참여자가 있어도 확정 명단 조회가 실패하지 않는다`()
    // given CONFIRMED, 회원 행 없는 확정 참여자
    // then 해당 행 nickname == null (대체 문구는 응답 조립 몫)

// 기존 F15 — 확정 명단도 참여자 수에 비례해 쿼리가 늘지 않는다
@Test fun `참여자가 늘어도 확정 명단 조회 쿼리 수는 그대로다`()
    // given CONFIRMED 2명 → 8명
    // then getConfirmedByRoom 쿼리 수 동일
```

## RoomParticipantServiceTest (단위 — 흐름)

```kotlin
@Test fun `명부 조회는 참여자 게이트를 통과한 뒤 현재 명단과 확정 명단을 함께 돌려준다`()
    // given validateParticipant 통과, reader 두 메서드 stub
    // then RoomParticipants(participants, confirmedParticipants), validateParticipant 1회 호출

@Test fun `참여자가 아니면 명단을 읽지 않고 E1419 로 거부한다`()
    // given validateParticipant 가 CoreException(E1419)
    // then 예외 전파, reader 호출 없음
```

## RoomConfirmedRosterCompletionIT (통합 — 이슈 재현 회귀)

실제 흐름: `RoomLeaveManager.leave` → `RoomParticipantService.getParticipants` → `RoomProgressManager.complete`.

```kotlin
@Test fun `확정 후 참여자가 나간 룸에서 명부의 확정 명단으로 완료하면 성공한다`()
    // given 최소 2, 방장+참여자 2명 확정(예정 시각 미래), 참여자 1명 leave → 룸 CONFIRMED 유지
    // when 방장이 명부를 조회해 confirmedParticipants 전원을 출석으로 complete
    // then 룸 COMPLETED, 출석 3건(나간 참여자 포함)
    //      대조: 같은 룸의 participants(현재 2명)만으로는 E1706 이었다 — 별도 단언으로 확인
```

## RoomParticipantControllerTest (RestDocs)

- `participants` 예시에 `confirmedParticipants` 2건 추가, 필드 설명:
  `data.confirmedParticipants[]` — 출석 입력 대상(확정 당시 참여자, 확정 후 이탈자 포함, 확정·완료 룸에서만 채워짐)
  `data.confirmedParticipants[].memberId`, `data.confirmedParticipants[].nickname`(탈퇴 시 "탈퇴한 회원")
- `participantsDescription`에 출석 입력은 `confirmedParticipants`를 쓴다는 문장 추가
