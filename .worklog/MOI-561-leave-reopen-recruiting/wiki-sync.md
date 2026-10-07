# MOI-561 wiki-sync (구현 전)

## 확인 범위

- PRD 「룸 참여 및 참여자 관리」 §3·§4.2·§4.6·§4.10·§2, 「룸 진행 확정」 §1·§2·§4.3
- 상태 SSOT `_src`: `기능/룸-참여-및-참여자-관리.yaml`, `공통.yaml`, `상태/룸.yaml`, `index.yaml`
- 요약: `sources/prd-룸-참여-및-참여자-관리-요약`, `sources/prd-룸-진행-확정-요약`

## 결정 근거

decisions.md 1~4 (2026-10-02 사람 결정).

## 갱신 내용

- 「룸 참여」: R23·R36·R58·R120 개정, 새 줄 R183(예정 시각 경과 예외)·R184(모집 재개 알림), 2026-10-02 변경 콜아웃
- 「룸 진행 확정」: R32 개정, 2026-10-02 변경 콜아웃
- SSOT: 새 전파 `X.participation.reopen_below_min`(T.participation.cancel), 새 알림 정책
  `P.notification.room_recruiting_reopened`(ROOM_RECRUITING_REOPENED), `G/C/T.participation.cancel` note·source,
  `P.room.lifecycle`·`P.room.confirmation_freeze`·`P.room.capacity` 문장, `F.room.status.written_by`, 기준_문서 날짜
- 요약 페이지 두 곳의 차단 서술

## 재조회 결과

- `ssot_load.py check`: 조립본 일치 · 조각 21개 통과. sync-manifest에 R183·R184 기록.
- `render_ssot.py --check`: 새 알림 정책이 "존재하지 않는 ROOM_RECRUITING_REOPENED"로 잡힌다.
  기존 `P.notification.*` 4건도 같은 이유로 잡히는 기존 패턴이다(렌더러가 코드의 이벤트 목록을 모른다).
- `wiki_lint` error: 이번 페이지와 무관한 1건(`raw/product/_index` 끊긴 링크)

## 코드·테스트 대응 (구현 후 대조)

| 명세 | 코드 | 테스트 |
| --- | --- | --- |
| R120 최소 인원이 나가기를 막지 않음 | `RoomLeaveManager.leave` 차단 제거, E1423 삭제 | `확정된 룸에서 인원이 최소와 같아도 참여자가 나가고 룸은 모집 중으로 돌아간다` |
| R120 최소 밑이면 모집 중 복귀·재확정 가능 | `reopenIfBelowMinCapacity` → `reopenRecruiting`(상태 + RECRUITING 이력) | 위 단위 테스트, IT `참여자 이탈로 모집이 재개된 룸은 새 신청을 수락해 재확정할 수 있다` |
| R120 최소 이상이면 확정 유지 | 같은 메서드의 인원 비교 | `…최소 인원 이상이 남으면 확정 상태가 유지된다` |
| R183 예정 시각 경과 시 확정 유지 | `RoomSchedule.isPassed` 검사 | `진행 예정 시각이 지난 …`(예정 시각 == 지금), `진행 예정 시각 직전에 …` |
| R184 방장·남은 참여자 알림, 나간 사람 제외 | `ROOM_RECRUITING_REOPENED` 발행, `NotificationComposer` 분기 | IT `…모집 재개 사실을 발행한다`(수신자 = 방장만), `NotificationComposerTest` 문구 |
| R36 방장 이탈 동작 유지 | `delegateOrCancel` 동작 동일(공통 메서드로만 추출) | 기존 RoomLeaveIT 방장 테스트 전부 통과 |

## 남은 불일치

- 탈퇴 관련 SSOT 낡은 기록은 MOI-540 tbd.md F절로 넘겼다(이 변경과 무관한 기존 서술).
