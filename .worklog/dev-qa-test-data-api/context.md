# dev-qa-test-data-api — 컨텍스트

**Linear 이슈 없음.** 사용자가 대화로 요구사항을 줬다(2026-09-22).

## 요약

QA 플랫폼(qa.agent.plady.io)이 dev 서버의 테스트 데이터를 버튼 하나로 지우고 초기화할 수
있도록 **dev 전용 Test API**를 `/v1/dev/...` 아래에 추가한다. 플랫폼은 REST Docs 산출물
(openapi3.yaml)을 읽어 폼과 버튼을 만들므로 요청·성공·에러 예시가 스펙에 전부 실려야 한다.

현재 문제: 공개 API에는 룸 하드 삭제·회원 초기화가 없다. 룸 취소는 모집 중일 때만 상태만
바꾸고, 테스트 계정(고정 UUID 2개)에 참여 중 룸이 쌓이면 E1425(참여 슬롯 3개)에 걸려 QA가 막힌다.

## 요구사항 핵심

원칙
1. `POST /v1/auth/dev-sessions`와 같은 게이트(`local`·`local-dev`·`dev`만 빈 등록, live는 404).
2. 경로는 전부 `/v1/dev/...`.
3. 제목이 `[QA]`로 시작하는 룸만 삭제. 아니면 409 + 새 에러 코드. 회원 행은 절대 삭제하지 않는다.
4. 응답 규약 동일(`{result, data, error{code,message}}`). 새 코드는 기존 번호 체계.
5. REST Docs로 요청·성공·에러(400 E400, 404, 409) 예시를 전부 문서화.
6. 호출마다 서버 로그 한 줄(무엇을 몇 건 지웠는지). 트랜잭션 하나, 자식→부모 순서.

API
1. `GET /v1/dev/qa-data?prefix=[QA]` → `data.rooms[]{roomId,title,status,hostMemberId,createdAt,counts{applications,participants}}`
2. `DELETE /v1/dev/rooms/{roomId}` → 룸 그래프 하드 삭제, `data.deleted{...}`; 404(E1405)/409(신설)
3. `DELETE /v1/dev/qa-data?prefix=[QA]&hostMemberId=` → 2번을 목록 전체에 적용, 건수 합계
4. `POST /v1/dev/members/{memberId}/reset` → 방장인 `[QA]` 룸 삭제, 본인 참가 신청·참여 행 삭제,
   `[QA]` 룸의 받은/쓴 후기 삭제. 방장인 룸 중 비QA가 있으면 409 전체 거절. 없는 회원 404(E1006).
5. (선택, 별도 PR) `POST /v1/dev/rooms/{roomId}/status` — 이번 범위 밖.

추가 지시: QA 전용 컨트롤러는 서비스 컨트롤러 사이에 섞이지 않게 별도 모듈 또는 별도 빈 묶음으로 둔다.

## 관련 코드

- `core/core-api/.../core/api/controller/v1/DevAuthController.kt` — 기존 dev 게이트 컨트롤러
- `core/core-api/.../core/api/auth/DevAccessTokenIssuer.kt` — `DEV_AUTH_PROFILE_EXPRESSION` 정의
- `core/core-api/src/test/.../core/api/auth/DevAuthProfileContextTest.kt` — 프로파일 게이트 테스트 패턴
- `storage/db-core/src/main/resources/schema.sql` — 룸에 매인 테이블(FK 제약 없음, `*_id` 컬럼만)
- `storage/db-core/.../ParticipationEntity.kt` — 방장 = `participation(role=HOST, status=JOINED)` 행
- `admin/admin-api` — 런타임 조립 모듈 패턴(참고만, 이번엔 패키지 분리로 충분)
- `security/security-core/.../SecurityConfig.kt` — `/v1/dev/**`는 `anyRequest authenticated`

## 룸에 매인 테이블 (schema.sql 기준, 자식 → 부모)

| 테이블 | 룸 연결 | 엔티티 |
| --- | --- | --- |
| guestbook_post | room_guestbook.id | GuestbookPostEntity |
| room_guestbook | room_id | RoomGuestbookEntity |
| review_tag | review.id | ReviewEntity @ElementCollection |
| review | room_id | ReviewEntity |
| review_skip | room_id | ReviewSkipEntity |
| attendance | room_id | AttendanceEntity |
| question_vote | closing_response.id / question.id | QuestionVoteEntity (ClosingResponse @OneToMany) |
| closing_response | room_id | ClosingResponseEntity |
| question_comment | question.id | QuestionCommentEntity |
| answer_summary | question.id | AnswerSummaryEntity |
| question (꼬리질문 포함) | room_id | QuestionEntity |
| round_feedback | room_id | RoundFeedbackEntity |
| round_assignment | interview_round.id | 엔티티 없음(native) |
| interview_round | interview_plan.id | 엔티티 없음(native) |
| interview_plan | room_id | 엔티티 없음(native) |
| resume_submission | room_id | ResumeSubmissionEntity |
| participation | room_id | ParticipationEntity |
| room_application | room_id | RoomApplicationEntity |
| room_status_log | room_id | RoomStatusLogEntity |
| room | id | RoomEntity |

## 경계 (하지 않는 것)

- 회원 삭제 API, 토큰·비밀번호 관련 변경, 공개 API 동작 변경, SecurityConfig 변경.
- `[QA]` 접두 검사 없는 삭제 경로, 프로파일 게이트 없는 경로, 소프트 삭제.
- 5번(룸 상태 강제)은 별도 PR.
