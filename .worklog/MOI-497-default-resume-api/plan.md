# MOI-497 구현 계획

## 현재 판정

기본 지정 서비스는 이미 구현·검증되어 있고 API 계약은 없다. 따라서
`requirement-implementation`에서 서비스 TDD를 반복하지 않고, 선행
`api-spec-definition`으로 목 Controller·RestDocs 계약을 먼저 확정한 뒤
`api-connection`으로 실제 `ResumeService.makeDefault`에 연결하는 것이 저장소
워크플로우에 맞다.

## 제안 계약

- 행위: 인증 회원이 소유한 AI 요약 완료 이력서를 기본으로 지정
- 확정 URI: `POST /v1/members/me/resumes/{resumeId}/make-default`
  - CRUD로 표현되지 않는 행위에 동사를 쓰는 API URI 규칙 적용
  - 화면의 이력서 행이 대상이므로 경로 식별자 외 요청 본문 없음
  - 같은 요청 반복을 성공 처리하는 기존 멱등 동작 유지
- 확정 성공 응답: `200 OK`와 공통 성공 봉투의 `data: null`
  - 기존 기본과 새 기본 두 행이 함께 바뀌므로 한 행만 반환하지 않음
  - 클라이언트는 로컬 목록 상태를 갱신하거나 목록 API를 재조회
- 오류: UUID 형식 오류 `400 E400`, 미인증 `401 E1102`, 인증 주체 회원 없음
  `404 E1006`, 존재하지 않거나 본인 소유가 아님 `404 E1010`, AI 요약
  미완료 `409 E1012`

## 변경 지점

### API 스펙 정의

- `ResumeApiControllerTest`: 성공·오류 RestDocs 테스트와 OpenAPI operation 추가
- `index.adoc`: 기본 이력서 지정 설명·요청·응답·오류 문서 추가

### 실제 연결

- `ResumeApiController`: 인증 회원 id와 경로 `resumeId`를
  `ResumeService.makeDefault`에 전달하고 공통 성공 응답 반환
- 새 Request DTO·Facade는 필요하지 않음

## 테스트 목록

- RestDocs 성공: 기본이 아닌 완료 이력서를 지정하면 `200`, `data: null`
- RestDocs 멱등: 이미 기본인 이력서를 다시 지정해도 `200`
- RestDocs 형식 오류: UUID가 아니면 `400 E400`
- RestDocs 소유권·존재 오류: 선택 불가능한 이력서는 `404 E1010`
- RestDocs 회원 오류: 탈퇴 등으로 인증 주체 회원이 없으면 `404 E1006`
- RestDocs 상태 오류: PROCESSING/FAILED 이력서는 `409 E1012`
- `ResumeApiControllerTest`: 실제 Controller와 mock Service 조합으로 호출 인자,
  공통 응답, 도메인 오류 전파와 RestDocs 계약 확인

## API 문서·외부 소비자 영향

- RestDocs와 생성 OpenAPI에 신규 operation·4xx 응답 예시가 추가된다.
- 외부 소비자는 마이페이지의 내 이력서 목록 프론트엔드다.
- 기존 이력서 목록·단건·등록·요약 재시도·삭제 계약은 변경하지 않는다.

## 영향 범위

- `core:core-api`의 Controller·RestDocs·AsciiDoc만 변경한다.
- DB 스키마, 외부 스토리지, Bedrock, 다른 모듈 의존에는 영향이 없다.

## API 스펙 구현 결과

- 스펙 단계의 local-dev·dev 임시 고정 응답 Controller는 실제 연결 단계에서 제거
- 사용자 결정에 따라 local `ResumeController`에도 신규 엔드포인트를 추가하지 않음
- 성공, 멱등, E400, E1006, E1010, E1012, E1102 RestDocs 계약 완료
- code-reviewer 필수 지적 3건 반영:
  - local-dev·dev 프로파일의 신규 경로 공백 해소
  - 실제 Service에서 발생 가능한 E1006 문서화
  - dev 실제 목록 UUID와 임시 목 고정 UUID 불일치 해소
- 검증 완료:
  - `./gradlew :core:core-api:restDocsTest --tests 'io.plady.moimyeon.core.api.controller.v1.ResumeApiControllerTest'`
  - `./gradlew :core:core-api:openapi3`
  - `./gradlew test ktlintCheck`
- OpenAPI operation `makeResumeDefault`에 200·400·401·404·409와
  E400·E1006·E1010·E1012·E1102 예시 병합 확인

## API 연결 결과

- `ResumeApiController.makeDefault`를 추가해
  `ResumeService.makeDefault(currentMember.id, resumeId)`에 직접 연결
- 단일 Service 호출이므로 Facade·Request/Response DTO 추가 없음
- local-dev·dev 임시 `ResumeDefaultMockController` 제거
- local `ResumeController`에는 기본 지정 엔드포인트를 제공하지 않음
- 신규 operation RestDocs를 초기 고정 응답 Controller에서 실제 `ResumeApiController` + mock
  `ResumeService` 조합으로 이전
- code-reviewer 배선 리뷰 통과: 필수·권장·참고 지적 없음
- ship-pr QA 게이트 PASS: 신규 상태 변경 API 위험도 medium,
  findings·coverage gaps 없음
- CodeRabbit: Resume API 프로파일 설명 지적 반영, 완료 단계 체크박스
  제안은 사람 승인 단계만 `[x]`로 표시하는 worklog 계약에 따라 미반영
- 최종 검증:
  - `./gradlew test ktlintCheck` 통과
  - `./gradlew :core:core-api:openapi3` 통과 및 계약 불변 확인
  - local-dev Docker 실호출 성공 200, 미존재 이력서 404 E1010 확인
  - 성공 후 MySQL `resume.is_default = 1` 확인
  - 상세 기록: `curl-verification.md`

## 단계

- [x] 계획 승인 (체크포인트 A, 2026-08-31: API 계약 결정 위임)
- [x] API 스펙 승인 (`api-spec-definition` 체크포인트 A,
  2026-08-31: 사용자 API 결정 위임에 따라 저장소 규칙·화면 근거로 확정)
- [x] API 스펙 구현 승인 (`api-spec-definition` 체크포인트 B,
  2026-08-31: 사용자 "이어서 진행해")
- [x] API 연결 계획 승인 (`api-connection` 체크포인트 A,
  2026-08-31: 승인된 계획에 따라 계속 진행)
- [ ] 실제 Service 연결 (`api-connection`) — 완료 (2026-08-31)
- [ ] 리뷰·검증 — 완료 (code-reviewer 통과, 전체 테스트·OpenAPI·실호출 통과)
- [x] API 연결 구현 승인 (`api-connection` 체크포인트 B,
  2026-08-31: 사용자 "다음 단계 진행해줘")
- [x] 구현 승인 (`requirement-implementation` 체크포인트 C,
  2026-08-31: 커밋·PR 단계 진행 승인)
- [x] 커밋·PR (2026-09-01, PR #117)

검증한 커밋: 4fef8f8e16d0a9d397940dbc65b9e3eecf932b8a
