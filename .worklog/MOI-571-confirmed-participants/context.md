# MOI-571 컨텍스트 — 출석 확인용 확정 당시 참여자 명단 조회 보완

- 이슈: [MOI-571](https://linear.app/100-thieves/issue/MOI-571) (상위 FE 이슈 [MOI-570](https://linear.app/100-thieves/issue/MOI-570))
- 관련 Wiki 원본
  - `raw/product/룸-진행-마무리-및-출석` — §4.2 R28, §4.6 R68·R71 (MVP 완료·출석)
  - `raw/product/룸-참여-및-참여자-관리` — §4.5 R102~R110 (명부), §4.6 R120·R183 (확정 후 이탈), §6 R177 (권한 회수)

## 요약

방장이 면접을 완료할 때 `POST /v1/rooms/{roomId}/complete`는 **최신 확정 시점의 참여자 전원**을
출석 대상으로 요구한다. 그런데 FE가 쓸 수 있는 명단인 `GET /v1/rooms/{roomId}/participants`는
**현재 JOINED 참여자만** 돌려준다. 확정 후 일반 참여자가 나가도 남은 인원이 최소 인원 이상이거나
예정 시각이 지났으면 룸은 CONFIRMED로 남으므로, 조회 명단으로 완료를 요청하면 E1706
(`ROOM_PROGRESS_PARTICIPANT_MISMATCH`)이 나고 재조회해도 복구할 수 없다.

## 변경 계약 (이슈 본문)

- 명부 응답 `data`에 `confirmedParticipants: [{ memberId, nickname }]` 추가
- CONFIRMED·COMPLETED: 완료 API와 **같은 기준**(최신 CONFIRMED 로그 시점)의 명단, 확정 후 이탈자 포함
- 그 외 상태: 빈 배열
- 기존 `participants`(현재 명단·이력서 권한)는 그대로. 확정 명단에는 이력서 정보 미노출 (R177)
- 완료 기준: 이탈자 있는 룸에서 조회→제출 성공, 재확정 룸은 최신 명단, 회귀 테스트·REST Docs/OpenAPI 갱신

## 관련 코드

| 위치 | 설명 |
| --- | --- |
| `core/core-api/.../api/controller/v1/RoomParticipantController.kt` | `GET /v1/rooms/{roomId}/participants` |
| `core/core-api/.../api/controller/v1/response/RoomParticipantsResponse.kt` | 응답 DTO (`participants`만 있음), `WITHDRAWN_PARTICIPANT_NICKNAME` |
| `core/core-api/.../api/facade/RoomParticipantFacade.kt` | 직무 이름 붙이는 응답 조립. 참여자가 없으면 바로 빈 응답 반환 |
| `core/core-api/.../domain/participation/RoomParticipantService.kt` | `getParticipants` — `validateParticipant`(JOINED만, E1419) 후 Reader 호출 |
| `core/core-api/.../domain/participation/RoomParticipantReader.kt` | `getAllByRoom` — JOINED만 조회, 이력서·원본 열람 판정 |
| `core/core-api/.../domain/participation/RoomParticipant.kt` | 명부 행 도메인 모델 ("명부에 있음 = JOINED" 전제 주석) |
| `core/core-api/.../domain/participation/ParticipationFinder.kt` | `getConfirmedParticipantIds`(완료 검증 기준), `wasConfirmedParticipant` |
| `core/core-api/.../domain/progress/RoomProgressManager.kt:48` | `complete` — 확정 명단과 요청 명단 대조(E1706). 자동 완료(`completeOverdue`)도 같은 명단 사용 |
| `storage/db-core/.../ParticipationRepository.kt:125` | `findAllAtRoomConfirmation` — 최신 CONFIRMED 로그 시각 기준 native 쿼리 |
| `core/core-api/.../domain/room/RoomLeaveManager.kt:171` | `reopenIfBelowMinCapacity` — 예정 시각 경과 또는 남은 인원 ≥ 최소면 CONFIRMED 유지 |
| `core/core-api/src/test/.../controller/v1/RoomParticipantControllerTest.kt` | 명부 REST Docs 테스트 |
| `.../test/.../participation/RoomParticipantServiceTest.kt`, `RoomParticipantReaderIT.kt`, `ParticipationFinderTest.kt` | 명부 관련 테스트 |
| `storage/db-core/src/test/.../ParticipationRepositoryIT.kt` | 확정 시점 쿼리 IT |

## 참고 관찰

- `RoomParticipantFacade.getParticipants`는 현재 참여자가 비면 조기 반환한다. 확정 명단을 붙이면 이 분기도 손봐야 한다.
- 명부 조회 게이트는 현재 JOINED만 통과한다. 이탈자 본인은 이 API를 못 부르며, 이슈 범위에서 바꾸지 않는다.
- 탈퇴로 나간 확정 참여자는 nickname이 비어 있다 — 기존 대체 문구(`탈퇴한 회원`) 적용 여부 확인 필요.
- `ParticipationRepository.kt:69`의 주석("countAtRoomConfirmation과 최신 CONFIRMED 선택 조건을 동일하게…")이
  `countOccupiedSlotsByMemberId` 위에 놓여 있다. 위치가 어긋난 것으로 보인다 (이슈 범위 밖).

## 이 작업의 경계 (하지 않는 것)

- 완료 API(`/complete`)의 검증 기준 변경
- 별도 출석 대상 API 신설 (이슈가 대안으로만 언급)
- 이탈자에게 명부 조회 권한 부여, 이력서·AI 요약 노출
- FE 구현(MOI-570)
