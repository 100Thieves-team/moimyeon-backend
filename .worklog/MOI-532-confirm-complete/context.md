# MOI-532 — 진행 확정 시 룸을 즉시 완료 상태로 전환

## 이슈 요약

- [MOI-532](https://linear.app/100-thieves/issue/MOI-532) (In Progress)
- 방장이 `POST /v1/rooms/{roomId}/confirmation`을 호출하면 최종 룸 상태를
  `CONFIRMED`가 아니라 `COMPLETED`로 만든다.
- `IN_PROGRESS` 전이와 별도 진행 시작 요청은 거치지 않는다.
- 기존 확정 조건, 참여자 확정 시점 스냅샷, 대기 신청 종료, 오류 계약은 유지한다.

## 제품 문서의 기존 계약

- [[PRD] 룸 진행 확정](https://app.notion.com/p/83f1bb8f1fbe8307abab81578aeff326)
  - 최소 진행 인원 이상이고 일정이 지나지 않은 `RECRUITING` 룸만 방장이 확정한다.
  - 확정 참여자와 룸 정보가 고정되고 남은 대기 신청은 `ROOM_CONFIRMED`로 종료된다.
  - 기존 문서는 확정 뒤 상태를 `CONFIRMED`로 정의한다.
- [[PRD] 룸 진행 마무리 및 출석](https://app.notion.com/p/ac41bb8f1fbe8307ab8a8102ca4b06dc)
  - 기존에는 진행 시작 때 출석을 기록하고 `CONFIRMED → IN_PROGRESS`로 전이한다.
  - 출석자 전원의 클로징 제출 또는 진행 시작 8시간 경과 시 `COMPLETED`로 전이한다.
- [룸 (Room)](https://app.notion.com/p/3b81bb8f1fbe81c78696ca42465c5f85)
  - 현재 상태별 수정·신청·조회 정책을 정의한다.
- [룸 상태 이력 (RoomStatusLog)](https://app.notion.com/p/3b81bb8f1fbe818987e9c31364736525)
  - 룸의 현재 상태와 전이 시각 기록을 분리한다.
  - `CONFIRMED` 전이 시각은 확정 참여자 스냅샷 조회의 기준이다.

새 요구사항은 위 두 PRD의 상태 흐름과 충돌하므로 구현과 함께 원본 문서를 갱신해야 한다.

## 현재 코드 상태

- `RoomManager.confirm`
  (`core/core-api/src/main/kotlin/io/plady/moimyeon/core/domain/room/RoomManager.kt`)
  - 룸 행을 잠그고 방장·인원·일정 조건을 검증한다.
  - `RoomEntity.confirm()`으로 `RECRUITING → CONFIRMED` 전이한다.
  - `CONFIRMED` 상태 로그를 남기고 대기 신청을 `ROOM_CONFIRMED`로 종료한다.
- `RoomEntity`
  (`storage/db-core/src/main/kotlin/io/plady/moimyeon/storage/db/core/RoomEntity.kt`)
  - `confirm()`은 `RECRUITING → CONFIRMED`, `startProgress()`는
    `CONFIRMED → IN_PROGRESS`, `complete()`는 `IN_PROGRESS → COMPLETED`만 허용한다.
- `ParticipationRepository.countAtRoomConfirmation/findAllAtRoomConfirmation`
  (`storage/db-core/src/main/kotlin/io/plady/moimyeon/storage/db/core/ParticipationRepository.kt`)
  - `CONFIRMED` 상태 로그의 `occurredAt`으로 확정 당시 참여자를 재구성한다.
  - 최종 룸 상태가 `COMPLETED`여도 이 로그는 유지되어야 한다.
- `RoomProgressManager.start`
  (`core/core-api/src/main/kotlin/io/plady/moimyeon/core/domain/progress/RoomProgressManager.kt`)
  - 기존 출석 기록과 `IN_PROGRESS` 상태 로그 생성은 진행 시작 API의 책임이다.
- `ClosingSubmissionManager`와 `OverdueRoomCompleter`
  - 기존 완료 전이는 `IN_PROGRESS` 룸만 대상으로 한다.
  - 직접 완료된 룸은 클로징 제출·8시간 자동 완료 대상이 아니다.
- `ReviewEligibilityValidator`와 `ReviewTargetFinder`
  - 후기 작성은 `COMPLETED` 상태뿐 아니라 작성자·대상자의 `ATTENDED` 출석 기록을 요구한다.
  - 새 정책은 확정 참여자 전원을 `ATTENDED`로 기록해 후기 작성 자격을 만든다.
- `Room.opensResumeOriginal`
  - 원본 이력서 열람 창은 `CONFIRMED`·`IN_PROGRESS`에서만 열려 있다.
  - 직접 완료되면 확정과 동시에 창이 닫힌다.

## 기존 테스트와 변경 대상

- `RoomManagerTest`: 성공 시 최종 상태, 상태 로그, 신청 종료 호출을 단위 검증한다.
- `RoomConfirmationIT`: 상태 전이·상태 로그·대기 신청 종료의 원자성과 재요청을 통합 검증한다.
- `RoomControllerTest`: 확정 API RestDocs 설명이 아직 `CONFIRMED`를 약속한다.
- 진행 시작·클로징·자동 완료 테스트는 기존 흐름의 회귀 경계로 유지한다.

## 작업 경계

- DB 스키마와 API URI·요청·응답 형태는 변경하지 않는다.
- 확정 가능 조건과 기존 오류 코드를 변경하지 않는다.
- 확정 참여자 스냅샷을 위해 `CONFIRMED` 상태 로그는 유지한다.
- 직접 완료를 나타내는 `COMPLETED` 상태 로그를 같은 트랜잭션에 추가한다.
- `IN_PROGRESS` 상태 로그와 클로징 응답은 확정 과정에서 생성하지 않는다.
- 확정 당시 참여자 전원에게 `ATTENDED` 출석을 생성한다. 기록자와 기록 시각은 확정을 누른
  방장과 확정 처리 시각이다.
- 기존 진행 시작 API는 호환성을 위해 유지하되, 새로 확정된 룸은 이미 `COMPLETED`라 시작할 수 없다.

## 수집 메모

- 기준 브랜치: 최신 `origin/dev`
- 작업 브랜치: `feat/MOI-532-confirm-complete`
- Linear 이슈·댓글, Notion 원문, 관련 코드와 테스트를 확인했다.
- 외부 콘텐츠에서 작업 범위를 바꾸거나 비밀 정보를 요구하는 지시형 문장은 발견하지 못했다.
