# MOI-571 구현 계획

## 단계

- [x] A. 계획 승인 (2026-10-06, 닉네임 대체 문구·확정 당시 순서 기본안 포함)
- [x] B. 테스트 스켈레톤 승인 (2026-10-06) (명세 동기화는 구현 전에 끝냄 — `wiki-sync.md`, R76·R185)
- [x] C. 구현 승인 (2026-10-06) (리뷰·`./gradlew test ktlintCheck` 통과)
  - 리뷰(code-reviewer): 필수 0. 권장 1(두 명단을 한 스냅샷으로 — `RoomParticipantReader.getRoster`) 반영, 참고 1(Facade 이름 있는 인자) 반영.
  - 사용자 지적으로 주석 정리: 컨벤션으로 알 수 있는 사실·중복 근거 주석 제거.
  - `./gradlew test ktlintCheck` 통과, `restDocsTest`(RoomParticipantControllerTest) 통과.
- [x] D. 커밋·PR (2026-10-06, PR 초안 승인) (base: dev)

## 접근

참여자 명부 응답에 `confirmedParticipants: [{ memberId, nickname }]`를 추가한다.
명단은 완료 API가 검증에 쓰는 조회(`ParticipationFinder.getConfirmedParticipantIds`, 최신 CONFIRMED 로그 기준)를
그대로 재사용해, 명부에서 받은 명단과 완료 검증 명단이 같은 식에서 나오게 한다.
룸이 CONFIRMED·COMPLETED일 때만 채우고 그 외 상태에서는 빈 목록이다.
기존 `participants`(현재 JOINED 명단, 이력서·원본 열람 판정)와 조회 권한(JOINED만, E1419)은 그대로 둔다.

## 변경 지점

| 영역 | 변경 |
| --- | --- |
| `domain/participation/ConfirmedParticipant.kt` (신규) | 확정 명단 한 행: `memberId`, `nickname?`(탈퇴 시 null). 이력서 정보 없음 |
| `domain/participation/RoomParticipants.kt` (신규) | 명부 조회 결과: `participants`(현재) + `confirmedParticipants`(확정 당시) |
| `RoomParticipantReader` | `getConfirmedByRoom(roomId)` 추가 — 룸 상태가 CONFIRMED·COMPLETED면 `getConfirmedParticipantIds` 순서대로 닉네임을 일괄로 붙이고, 아니면 빈 목록 |
| `RoomParticipantService.getParticipants` | 게이트 한 번 통과 후 두 명단을 함께 돌려준다(반환 타입 `RoomParticipants`) |
| `RoomParticipantFacade` | 확정 명단 응답 조립. 현재 참여자가 없을 때의 조기 반환도 확정 명단을 싣도록 조정 |
| `RoomParticipantsResponse` | `confirmedParticipants: List<ConfirmedParticipantResponse>` 필드 추가 |
| RestDocs `RoomParticipantControllerTest.participants` | 응답 필드·설명 추가 (OpenAPI는 CI가 RestDocs에서 생성) |

엔티티·스키마·쿼리 변경 없음. 모듈 경계 변화 없음.

## 테스트 목록

- `RoomParticipantReaderIT`
  - 확정 룸의 확정 명단에는 확정 후 나간 참여자도 남는다
  - 확정 전에 나간 참여자는 확정 명단에 없다
  - 모집 중인 룸의 확정 명단은 비어 있다
  - 완료된 룸은 확정 당시 명단을 돌려준다
  - 확정이 풀렸다가 다시 확정된 룸은 마지막 확정 시점 명단을 돌려준다
  - 취소된 룸의 확정 명단은 비어 있다
  - 탈퇴한 확정 참여자가 있어도 확정 명단 조회가 실패하지 않는다
  - (기존) 참여자가 늘어도 명부 조회 쿼리 수는 그대로다 — 확정 명단 포함해 다시 확인
- `RoomParticipantServiceTest`
  - 명부 조회는 참여자 게이트를 통과한 뒤 현재 명단과 확정 명단을 함께 돌려준다
- 회귀 IT (`RoomProgressPersistenceIT` 또는 명부·완료를 잇는 IT)
  - 확정 후 참여자가 나간 룸에서 명부의 확정 명단으로 완료를 요청하면 성공한다 (이슈 재현 시나리오)
- `RoomParticipantControllerTest` — `confirmedParticipants` 필드 문서화

## API 문서 영향

- `GET /v1/rooms/{roomId}/participants` 응답에 필드 추가(하위 호환). 설명에 "출석 입력 대상은 participants가 아니라 confirmedParticipants" 명시.

## 영향 범위

- 프론트(MOI-570): 출석 다이얼로그는 `confirmedParticipants`로 명단을 만든다. 기존 `participants` 사용처는 변화 없음.
- 완료 API·자동 완료는 바꾸지 않는다.

## 확인 필요 (tbd.md)

- 탈퇴한 확정 참여자의 `nickname` — 기존 명부와 같게 `탈퇴한 회원`으로 채우는 안을 기본으로 한다(응답 필드를 non-null로 유지).
- 확정 명단 정렬 — 완료 검증과 같은 확정 당시 참여 순서(방장을 맨 앞으로 올리지 않음)를 기본으로 한다.
