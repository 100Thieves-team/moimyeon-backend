# MOI-497 - 이력서 "기본으로 지정" API 추가 및 연결

## 이슈 요약

- [MOI-497](https://linear.app/100-thieves/issue/MOI-497/이력서-기본으로-지정-api-추가-및-연결)
  (In Progress, 회원 및 프로필 프로젝트)
- 내 이력서 목록에서 선택한 이력서를 기본 이력서로 지정하는 API를 추가하고
  실제 서비스에 연결한다.
- 이슈 기준으로 서비스 로직이 있으면 API만 추가·연결하고, 없으면 서비스까지
  구현한다. 코드를 확인한 결과 서비스 로직은 이미 존재한다.
- 이슈 코멘트·첨부 문서·연결된 문서는 없다. Notion에서 이슈 제목과
  `회원 및 프로필`, `이력서`, `기본 지정` 조합으로 검색했으나 이 작업의 계약을
  정하는 팀 PRD는 찾지 못했다. Linear 프로젝트의 원문 PRD 링크는 확인했지만
  현재 Notion 연결에는 페이지 접근 권한이 없다.
- 같은 프로젝트의 [MOI-485 이력서 관리 페이지](https://linear.app/100-thieves/issue/MOI-485/이력서-관리-페이지)
  화면에서 비기본 이력서 행에 `기본으로 지정` 버튼이 있고, 현재 기본 이력서에는
  `기본` 배지가 표시되는 것을 확인했다. 별도 입력값이 없는 행 단위 상태 변경이다.

## 현재 코드 상태

- `ResumeService.makeDefault(memberId, resumeId)`가 이미
  `ResumeManager.makeDefault`에 위임한다.
- `ResumeManager.makeDefault`는 회원 행 잠금으로 같은 회원의 기본 변경을
  직렬화하고, 기존 기본 해제 후 flush하여 회원별 기본 유니크 제약을 안전하게
  넘긴 다음 선택 이력서를 기본으로 지정한다.
- 이미 기본인 이력서의 재지정은 성공하는 멱등 동작이다.
- 존재하지 않거나 본인 소유가 아닌 이력서는 `E1010(RESUME_NOT_FOUND)`,
  AI 요약이 완료되지 않은 이력서는 `E1012(RESUME_NOT_READY)`로 실패한다.
- 서비스 단위 테스트, Manager 단위 테스트, 실제 DB 통합 테스트가 위 동작과
  동시 변경 결과를 이미 검증한다.
- `ResumeApiController`와 local 프로파일에서 이력서 고정 응답을 제공하는
  `ResumeController`에는 기본 지정
  엔드포인트가 없다. `ResumeControllerTest`와 AsciiDoc에도 해당 RestDocs
  계약이 없다.

## 관련 코드 위치

- `core/core-api/src/main/kotlin/io/plady/moimyeon/core/domain/resume/ResumeService.kt`:
  기본 지정 서비스 진입점
- `core/core-api/src/main/kotlin/io/plady/moimyeon/core/domain/resume/ResumeManager.kt`:
  소유권·요약 상태 판정, 회원 잠금, 기존 기본 해제와 새 기본 지정
- `storage/db-core/src/main/kotlin/io/plady/moimyeon/storage/db/core/ResumeEntity.kt`:
  기본 지정·해제 상태 전이
- `storage/db-core/src/main/kotlin/io/plady/moimyeon/storage/db/core/ResumeRepository.kt`:
  현재 기본 및 선택 가능한 이력서 조회
- `core/core-api/src/main/kotlin/io/plady/moimyeon/core/api/controller/v1/ResumeApiController.kt`:
  실 API 연결 대상
- `core/core-api/src/main/kotlin/io/plady/moimyeon/core/api/controller/v1/ResumeController.kt`:
  local 프로파일의 이력서 고정 응답 Controller
- `core/core-api/src/test/kotlin/io/plady/moimyeon/core/api/controller/v1/ResumeControllerTest.kt`:
  RestDocs·OpenAPI 계약 테스트 대상
- `core/core-api/src/test/kotlin/io/plady/moimyeon/core/domain/resume/ResumeServiceTest.kt`:
  기존 서비스 위임 테스트
- `core/core-api/src/test/kotlin/io/plady/moimyeon/core/domain/resume/ResumeManagerTest.kt` 및
  `ResumeManagementIT.kt`: 기존 도메인 규칙·DB 원자성 검증
- `core/core-api/src/docs/asciidoc/index.adoc`: Resume API 문서

## 작업 경계

- 새 서비스·엔티티·Repository·마이그레이션은 만들지 않는다.
- 기본 이력서 선정·해제 정책과 AI 요약 완료 조건은 바꾸지 않는다.
- 이력서 이름 변경 API는 이번 이슈 범위가 아니다.
- 프론트엔드 코드는 이 저장소에 없으므로 외부 소비자용 API 계약 제공까지만
  범위에 포함한다.
