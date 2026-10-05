# MOI-561 구현 계획

## 단계

- [x] A. 계획 승인 (2026-10-02)
- [x] B. 명세 동기화(wiki-sync) + 테스트 스켈레톤 승인 (2026-10-02)
- [x] C. 구현 승인 (2026-10-02) (리뷰·`./gradlew test ktlintCheck` 통과)
  - 리뷰: 필수 1(남은 주석) 반영, 권장 2(중복 테스트 통합) 반영. `./gradlew test ktlintCheck` 통과, `restDocsTest`(RoomParticipantControllerTest) 통과
- [ ] D. 커밋·PR (base: dev, 위에 MOI-540 스택)

## 접근

확정 룸에서 일반 참여자가 나갈 때의 최소 인원 차단(E1423)을 없앤다.
나간 뒤 JOINED 인원이 최소 진행 인원보다 적으면, 방장 이탈 때와 같은 방식으로 룸을 모집 중으로 되돌린다.
이때 상태 이력(RECRUITING)을 남기고, 모집 재개 이벤트를 발행해 방장과 남은 참여자에게 알린다.
같은 룸 행 잠금 안에서 처리하므로 동시 이탈에서도 판정이 어긋나지 않는다(기존 잠금 유지).

결정: 진행 예정 시각이 지난 확정 룸(자동 완료 8시간 대기 중)에서는 참여자가 나가도 모집을 재개하지 않는다.
면접이 이미 열렸을 수 있다. 되돌리면 일정이 지나 새 신청을 받을 수 없고, 최소 인원 미달이라 재확정도 못 해
룸이 모집 중에 멈춘다. 그러면 완료·출석·후기로 이어지지 않는다.
→ 예정 시각 전에만 모집을 재개하고, 지난 뒤에는 확정 상태를 유지한다(나간 사람은 확정 후 이탈로 남고 출석에서 처리).

## 변경 지점

| 영역 | 변경 |
| --- | --- |
| `RoomLeaveManager` | `requireAboveMinCapacity` 제거. 일반 참여자 이탈 후 인원 < 최소면 모집 재개 + 상태 이력 + 이벤트 발행. 방장 이탈의 모집 재개 코드와 한 곳으로 모은다 |
| `CoreErrorType`/`ErrorCode` | `ROOM_AT_MIN_CAPACITY`/`E1423` 삭제 (번호는 비워 둔다. E1420·E1426·E1428 선례) |
| 이벤트 | 새 이벤트 종류와 payload(룸·제목·방장·남은 참여자). `EventType`, `EventPayloadClass` 짝 추가 |
| `NotificationComposer` | 방장·남은 참여자별 문구. 나간 사람은 대상이 아니다 |
| RestDocs | `roomLeaveAtMinCapacity` 삭제, `leaveDescription` 문구 갱신. `index.adoc` 오류 표 확인 후 `openapi3` 재생성 |

## 테스트 목록

- `RoomLeaveManagerTest`
  - 확정 룸에서 인원이 최소와 같아도 참여자가 나갈 수 있다 (기존 E1423 테스트 대체)
- `RoomLeaveIT`
  - 확정 룸에서 참여자가 나가 최소 인원보다 적어지면 모집 중으로 돌아가고 상태 이력이 남는다
  - 확정 룸에서 참여자가 나가도 최소 인원 이상이면 확정 상태가 유지된다
  - 모집 재개 후 새 신청을 수락해 재확정할 수 있다
  - 참여자 이탈로 모집이 재개되면 방장과 남은 참여자를 담아 모집 재개 사실을 발행한다
  - 모집 중인 룸이나 최소 인원 이상이 남는 이탈은 사실을 발행하지 않는다 (기존 테스트 이름 조정)
  - 예정 시각이 지난 확정 룸에서는 참여자가 나가도 확정 상태가 유지된다
- `NotificationComposerTest`: 모집 재개 알림이 방장·남은 참여자에게 구분된 문구로 간다
- `OutboxEventSerializerTest`: 새 payload 직렬화
- `RoomParticipantControllerTest`: E1423 문서 삭제

## 명세 동기화 (wiki-sync, 구현 전)

- PRD 「룸 참여」: R23, R58, R120과 20260811 콜아웃 개정(변경 콜아웃 추가), §5 "진행 확정 이후 참여자 변경" 확인
- PRD 「룸 진행 확정」: R31, R32 개정, R48 문구 확인
- 상태 SSOT `_src`: 참여자 이탈의 모집 재개 연쇄 추가, `F.room.status.written_by`,
  `G.participation.cancel` note, `P.room.lifecycle`·`P.room.confirmation_freeze`, 새 알림 정책
- 요약 페이지 `sources/prd-룸-참여-및-참여자-관리-요약`, `sources/prd-룸-진행-확정-요약`의 차단 서술

## 영향

- 프론트: 참여 취소 버튼 비활성·사유 표시(E1423) 제거. 새 알림 종류 표시.
- 기존 E1423을 받는 클라이언트 경로가 사라진다. 배포 전 서비스라 호환 처리는 하지 않는다.
- MOI-540(회원 탈퇴)은 이 변경 위에서 나가기를 예외 없이 호출한다.
