# MOI-541 구현 계획

## API 계약

- `POST /v1/rooms/{roomId}/complete`: 방장 수동 완료. 요청 본문 없음.
- `POST /v1/rooms/{roomId}/attendances`: 완료 후 방장이 확정 참여자 전원의 `ATTENDED|ABSENT`를 한 번 기록.
- `GET /v1/attendances/me`: 완료 후 내 출석 조회.
- 명시적 룸 취소 및 진행 레일·진행 중 질문/댓글·라운드 피드백 API는 제거.

## 구현

- 룸 상태 enum과 상태 의존 코드에서 `IN_PROGRESS` 제거.
- 확정 방장 이탈 + 후임 위임 시 `RECRUITING` 로그를 추가하고 재확정 가능하게 변경.
- 반복 전이 허용 마이그레이션과 최신 확정 명단 쿼리 적용.
- 수동 완료와 출석 저장을 별도 트랜잭션으로 분리.
- worker가 예정 시각 8시간 경과 후보를 찾고 잠금 후 재검증해 자동 완료.
- 완료·리뷰 요청 알림을 Outbox로 기록.

## 검증

- 상태 엔티티, 방장 이탈 DAG, 반복 확정 명단, 수동 완료, 완료 후 출석, 자동 완료 경계 테스트.
- RestDocs/OpenAPI에서 제거된 API와 신규 계약 확인.
- ktlint, unit/context 테스트, 스키마 마이그레이션 검증.

## 체크포인트

- [x] Linear 이슈와 DAG 확정
- [x] 구현 승인
- [x] 구현·검증 완료
- [ ] PR 및 배포 승인
