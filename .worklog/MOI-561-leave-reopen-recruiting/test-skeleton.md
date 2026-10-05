# MOI-561 테스트 스켈레톤

근거: 「룸 참여」 §4.6 R120과 새 줄(예외·알림), 「룸 진행 확정」 §4.3 R32. 결정은 decisions.md.
진행 예정 시각 판정은 기존 `RoomSchedule.isPassed`(예정 시각 <= 지금이면 지남)를 따른다.

## RoomLeaveManagerTest (단위 — 나가기·모집 재개 판정)

```kotlin
// 기존 `확정된 룸에서 인원이 최소와 같으면 E1423 을 던진다` 를 대체한다
@Test fun `확정된 룸에서 인원이 최소와 같아도 참여자가 나갈 수 있다`()
    // given CONFIRMED, 최소 3, JOINED 3(나가기 전), 예정 시각 미래
    // when 일반 참여자 leave
    // then 참여 LEFT, 예외 없음

@Test fun `확정된 룸에서 참여자가 나가 최소 인원보다 적어지면 모집 중으로 돌아간다`()
    // given CONFIRMED, 최소 3, 나간 뒤 JOINED 2, 예정 시각 미래
    // then room.status == RECRUITING, RECRUITING 상태 이력 저장, 모집 재개 이벤트 발행

@Test fun `확정된 룸에서 참여자가 나가도 최소 인원 이상이 남으면 확정 상태가 유지된다`()
    // given CONFIRMED, 최소 3, 나간 뒤 JOINED 3
    // then CONFIRMED 유지, 상태 이력·이벤트 없음

@Test fun `진행 예정 시각이 지난 확정 룸에서는 참여자가 나가 최소 인원보다 적어져도 확정 상태가 유지된다`()
    // given CONFIRMED, 최소 3, 나간 뒤 JOINED 2, 예정 시각 == 지금 (경계: 지난 것으로 본다)
    // then CONFIRMED 유지, 참여 LEFT, 상태 이력·이벤트 없음

@Test fun `진행 예정 시각 직전에 나가 최소 인원보다 적어지면 모집 중으로 돌아간다`()
    // given 예정 시각 == 지금 + 1분 — 위 테스트의 반대쪽 경계
    // then RECRUITING

@Test fun `모집 중인 룸에서 참여자가 나가면 상태가 바뀌지 않는다`()
    // given RECRUITING, 나간 뒤 JOINED < 최소
    // then RECRUITING 그대로, 상태 이력·이벤트 없음
```

기존 유지: `모집 중인 룸에서는 참여자가 자유롭게 나간다`, `확정된 룸도 인원이 최소보다 많으면 나갈 수 있다`,
`완료되거나 취소된 룸에서는 나갈 수 없다`, `참여 중이 아니면 E1419 를 던진다`.

## RoomLeaveIT (통합 — 한 커밋·실제 흐름·이벤트)

```kotlin
@Test fun `확정 룸에서 참여자가 나가 최소 인원보다 적어지면 모집 중으로 돌아가고 상태 이력이 남는다`()
    // given 최소 2, 방장+참여자 1 확정 → 참여자 leave
    // then DB 상 RECRUITING, room_status_log RECRUITING 존재, 참여 LEFT

@Test fun `참여자 이탈로 모집이 재개된 룸은 새 신청을 수락해 재확정할 수 있다`()
    // then 신청 수락 후 roomManager.confirm 성공, CONFIRMED 이력 2건

@Test fun `참여자 이탈로 모집이 재개되면 방장과 남은 참여자를 담아 모집 재개 사실을 발행한다`()
    // then 모집 재개 payload 1건: hostMemberId == 방장, participantMemberIds 에 나간 참여자 없음

// 기존 `방장이 아닌 참여자가 나가면 사실을 발행하지 않는다` 이름 조정
@Test fun `방장이 아닌 참여자가 나가도 모집이 재개되지 않으면 사실을 발행하지 않는다`()

@Test fun `확정 후 이탈자는 확정 참여자 판정에 걸린다`()  // 기존 유지 — 모집 재개 뒤에도 성립하는지 확인
```

방장 이탈 테스트(위임·모집 재개·취소·위임 이벤트)는 그대로 둔다. 방장 이탈로 모집이 재개될 때는
위임 알림만 가고 모집 재개 알림은 가지 않는다(지금 동작 유지) — 기존 위임 이벤트 테스트가 고정한다.

## NotificationComposerTest

```kotlin
@Test fun `모집 재개는 방장과 남은 참여자에게 PUSH_ELSE_EMAIL 로 알리고 방장에게는 다른 문구를 보낸다`()
    // then 방장: "참여자가 나가 모집이 다시 열렸어요", 참여자: "모집이 다시 열렸어요", 본문에 룸 제목
```

알림 정책(PUSH_ELSE_EMAIL)은 같은 성격의 방장 위임 알림을 따른다.

## OutboxEventSerializerTest

`모든 이벤트 종류가 저장한 모양 그대로 되읽힌다` 에 새 이벤트 종류의 샘플 payload 추가 (기존 테스트가 전 종류를 순회).

## RoomParticipantControllerTest

`leaveRoomAtMinCapacity`(E1423) 삭제, `leaveDescription` 을 새 규칙으로 갱신. 새 오류 코드 없음.
