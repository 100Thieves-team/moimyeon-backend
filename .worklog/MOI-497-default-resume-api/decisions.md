# MOI-497 결정 기록

## DR-001: 기본 지정은 이력서 하위 행위 API로 표현한다

- 결정: `POST /v1/members/me/resumes/{resumeId}/make-default`
- 이유: 화면에서 특정 이력서 행의 `기본으로 지정` 버튼을 누르는 행위이고,
  CRUD로 표현되지 않는 행위에는 동사를 쓰는 저장소 URI 규칙을 따른다.
- 요청 본문: 없음. 대상은 인증 주체와 경로 식별자로 온전히 결정된다.

## DR-002: 성공 응답은 변경 이력서 한 건을 반환하지 않는다

- 결정: `200 OK`, `ApiResponse.success()`의 `data: null`
- 이유: 기본 지정은 선택한 행을 true로 바꾸는 동시에 기존 기본 행을 false로
  바꾼다. 한 행만 반환하면 목록 전체의 최신 상태를 표현하지 못한다.
- 소비자: 성공 후 로컬 목록에서 두 행을 함께 갱신하거나 목록 API를 재조회한다.

## DR-003: 기존 도메인 정책과 오류 코드를 그대로 노출한다

- `E1006`: 탈퇴 등으로 인증 주체 회원이 존재하지 않음
- `E1010`: 존재하지 않거나 본인 소유가 아닌 이력서
- `E1012`: AI 요약이 PROCESSING 또는 FAILED인 이력서
- 같은 이력서를 다시 지정하면 `200`으로 성공하는 멱등 동작 유지

## DR-004: local-dev·dev는 실제 Service 연결을 사용한다

- local-dev는 서버를 로컬에서 실행하면서 dev 환경 인프라를 사용하는
  프로파일이므로 `ResumeApiController`와 실제 Service를 사용한다.
- dev·staging·live도 같은 실제 Controller 연결을 사용한다.
- RestDocs는 실제 Controller와 mock Service 조합으로 작성해 API 계약과
  배선을 함께 검증한다.

## DR-005: local의 고정 응답 Controller에는 기본 지정 API를 두지 않는다

- local 프로파일 자체는 mock을 뜻하지 않는다. 목 여부는 개별 Bean 구현으로
  판단하며, `ResumeController`가 이력서 영역에서 고정 응답을 제공하는 구현이다.
- 사용자 결정에 따라 local `ResumeController`의 기본 지정 엔드포인트를
  최종 변경에서 제거했다.
- 신규 경로는 실제 Service가 조립되는 local-dev·dev·staging·live에서만
  제공한다.
