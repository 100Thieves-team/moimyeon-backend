# MOI-571 Wiki 동기화 기록

## 확인 범위

- `raw/product/룸-참여-및-참여자-관리` — §4.5 R101~R113(상세 명부), §4.6 R119·R120·R183(확정 후 이탈), §6 R177(접근 회수)
- `raw/product/룸-진행-마무리-및-출석` — §4.2 R25·R28(확정 참여자 명단), §4.6 R68·R71(MVP 완료·출석)
- 상태 SSOT `policy/_src/` — `D.participation.confirmed_member`, `F.room.confirmed_at`, `G.room.read_participants`, `G.room.complete`

## 결정 근거

- 확정 후 이탈자를 출석 대상에 넣는 기준은 새로 정한 정책이 아니다. SSOT
  `D.participation.confirmed_member`가 이미 "확정 후 이탈은 제외하지 않는다"고 정의하고,
  PRD R28은 확정 전 이탈자만 제외한다. 백엔드 완료 검증(`ParticipationFinder.getConfirmedParticipantIds`)도 같은 기준이다.
- 재확정 룸은 `T.room.confirm`이 `F.room.confirmed_at`을 새로 쓰므로 마지막 확정 시점 명단이 된다.
  코드는 최신 CONFIRMED 상태 로그를 기준으로 한다(`ParticipationRepository.findAllAtRoomConfirmation`).
- 명부 응답에 확정 명단을 싣는 계약은 이슈 MOI-571 본문의 변경 계약을 따른다.

## 갱신 내용

| 문서 | 변경 |
| --- | --- |
| 「룸 진행 마무리 및 출석」 | R76 신설 — 확정 상태로 남은 룸의 확정 후 이탈자는 출석 대상, 재확정 룸은 마지막 확정 시점 명단. `next_req` 77, `updated` 2026-10-06 |
| 「룸 참여 및 참여자 관리」 | R185 신설 — 확정·완료 룸의 상세 참여자 목록에 확정 참여자 명단(닉네임만), 다른 상태는 빈 명단. `next_req` 186, `updated` 2026-10-06 |
| `상태/참여.yaml` | `D.participation.confirmed_member` source에 R76, note에 재확정 기준 |
| `기능/룸-참여-및-참여자-관리.yaml` | `G.room.read_participants` source에 R177·R185·R76, note에 확정 명단 |
| `기능/룸-진행-마무리-및-출석.yaml` | `G.room.complete` attendance-roster-matches note와 source에 R76·R185 |
| `index.yaml` | 기준_문서 두 PRD를 2026-10-06으로 |

기존 요구사항 줄의 문구는 바꾸지 않았다(드리프트 대상 없음).

## 검증

- 갱신 전 로컬 사본에서 `ssot_load.py assemble`·`check` 통과(조각 21개), `render_wiki.py` 검증 결과가
  갱신 전과 같음(치명 27 · 중대 104 · 경미 158 — 모두 기존 항목).
- MCP 쓰기 6건의 바이트 수가 로컬 수정본과 일치, `wiki_content_read`로 재조회해 반영 확인.
- Wiki Git 원격(main)의 6개 파일이 로컬 수정본과 바이트 단위로 같음을 확인.

## 코드·테스트 대응

구현 후 대조 결과(남은 불일치 없음):

| 항목 | 코드 | 테스트 |
| --- | --- | --- |
| R185 확정·완료 룸에서만 채움, 그 외 빈 배열 | `Room.hasConfirmedRoster`, `RoomParticipantReader.getConfirmedByRoom` | ReaderIT 모집 중·취소·완료 케이스 |
| R185 memberId·닉네임만, 이력서 정보 없음 | `ConfirmedParticipant`, `ConfirmedParticipantResponse` | RestDocs `roomParticipants` |
| R76 확정 후 이탈자 포함 / 확정 전 이탈자 제외 | `getConfirmedParticipantIds` 재사용(완료 검증과 같은 조회) | ReaderIT 두 경계 케이스 |
| R76 재확정 룸은 마지막 확정 시점 | 같은 조회(최신 CONFIRMED 로그) | ReaderIT 재확정 케이스 |
| R68·R76 이 명단으로 완료 가능 | — | `RoomConfirmedRosterCompletionIT` |
