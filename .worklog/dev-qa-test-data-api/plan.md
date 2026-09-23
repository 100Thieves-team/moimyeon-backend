# dev-qa-test-data-api — 계획

이슈 키 없음. 사용자가 준 요구사항(context.md)이 곧 계획이며, 사용자가 "진행해줘"로
구현까지 위임했다. 체크포인트 A·B는 사용자 지시로 생략하고, PR 초안(체크포인트)에서 멈춘다.

## 접근

- QA 전용 코드는 `io.plady.moimyeon.core.qa` 한 패키지에 모은다. 서비스 컨트롤러
  (`core.api.controller.v1`)와 섞지 않는다. 컨트롤러 1개(`QaTestDataController`),
  Service 1개, Implement 3개(Finder·Eraser·Resetter), db-core 저장소 1개.
- 프로파일 게이트는 `POST /v1/auth/dev-sessions`와 같은 `DEV_AUTH_PROFILE_EXPRESSION`을
  QA 패키지의 모든 빈에 붙인다. 패키지 안 빈이 전부 게이트를 갖는지 아키텍처 가드
  테스트로 강제한다.
- 하드 삭제 쿼리(JPQL·native)는 db-core의 `QaTestDataRepository` 한 클래스에 둔다.
  기존 운영 Repository에는 QA 전용 메서드를 추가하지 않는다.
- 새 에러 코드 E2201 `QA_DATA_ONLY`(409).

## 단계

- [x] 1. worktree 준비 (`feat/dev-qa-test-data-api`, origin/dev 기준)
- [x] 2. 컨텍스트 수집 → context.md (사용자 요구사항 + 코드 위치)
- [x] 3. 구현 계획 (이 문서) — 체크포인트 A: 사용자 지시("진행해줘")로 생략
- [ ] 4. service 테스트 스켈레톤 — 체크포인트 B: 생략, TDD 구현과 함께 진행 (실행 완료)
- [ ] 5. TDD 구현
  - db-core `QaTestDataRepository` (룸 그래프 삭제 쿼리, 회원 행 삭제 쿼리, 조회)
  - `ErrorCode.E2201` + `CoreErrorType.QA_DATA_ONLY`
  - `core.qa` 도메인: `QaRoom`, `QaDeletedRows`, `QaDataCondition`
  - Implement: `QaRoomFinder`, `QaRoomEraser`, `QaMemberResetter`
  - `QaTestDataService`, `QaTestDataController` + request/response DTO
  - 테스트: Service 단위(MockK), Eraser·Resetter IT(H2), 프로파일 게이트 컨텍스트 테스트,
    패키지 게이트 가드 테스트, RestDocs(성공·E400·404·409), QaDataCondition·QaDeletedRows 단위,
    QaTestDataRepositoryMySqlIT(Testcontainers)
  - index.adoc 절 + `docs/conventions/api-design.md` dev Test API 절
- [ ] 6. 리뷰 (code-reviewer, db-reviewer, qa-reviewer) — 실행 완료. 필수 전부 반영:
  마커 대소문자 무시(db·qa), hostMemberId String 파싱 E400(code), 로그 접두 원문 제거(code),
  MySQL Testcontainers 저장소 테스트(qa). qa-reviewer 판정 CONDITIONAL — 인가 수용 여부는 tbd.md.
- [ ] 7. 검증 `./gradlew test ktlintCheck` + `restDocsTest` + `openapi3` — 실행 완료(통과)
- [ ] 8. (추가 지시) 시나리오 준비 API 3개: 룸 시작 시각 변경·테스트 회원 생성·이력서 요약 완료 강제 — 구현·테스트·문서 완료
- [ ] 9. (추가 지시) QA 생성 회원 하드 삭제: `DELETE /v1/dev/members/{memberId}`, 일괄 삭제 `includeMembers`, 목록 `members[]`
- [ ] 10. 커밋·PR 초안 — 체크포인트: PR 초안 승인 후 push

## 만들 테스트

- `QaTestDataServiceTest` — 흐름: finder/eraser/resetter 위임과 예외 전파
- `QaRoomEraserIT` — 룸 그래프 전 테이블 삭제 건수, 비QA 룸 409, 없는 룸 404, 다른 룸 보존
- `QaMemberResetterIT` — 방장 QA 룸 삭제 + 타 룸 참여·신청 삭제 + QA 룸 후기 삭제, 비QA 방장 룸 409 전체 거절, 없는 회원 404
- `QaTestApiProfileContextTest` — local/local-dev/dev 등록, staging/live 미등록
- `QaPackageProfileGateTest` — `core.qa` 패키지의 모든 스테레오타입 빈이 게이트 `@Profile`을 가진다
- `QaTestDataControllerTest` (RestDocs) — 4개 연산 성공 + E400/E1405/E1006/E2201

## API 문서 영향

- openapi3.yaml에 `/v1/dev/qa-data`(GET·DELETE), `/v1/dev/rooms/{roomId}`(DELETE),
  `/v1/dev/members/{memberId}/reset`(POST) 추가. 기존 연산 변경 없음.
- 외부 소비자: QA 플랫폼(qa.agent.plady.io)이 이 스펙을 읽는다. 프론트 영향 없음.

## 영향 범위

- 공개 API 동작 변경 없음. SecurityConfig 변경 없음(`/v1/dev/**`는 기본 `authenticated`).
- 스키마 변경 없음.
