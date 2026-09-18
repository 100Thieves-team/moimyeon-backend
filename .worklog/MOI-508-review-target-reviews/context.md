# MOI-508 - 후기 대상 응답에 작성한 후기 포함

## 이슈 요약

- [MOI-508](https://linear.app/100-thieves/issue/MOI-508) (In Progress, bebe 담당)
- 후기 작성 화면은 기존 `GET /v1/rooms/{roomId}/review-targets`로 대상과 제출 상태를 먼저 조회한 뒤,
  제출된 대상마다 `GET /v1/reviews/{reviewId}`를 추가 호출하고 있다.
- 룸별 후기 작성 개요 응답에 현재 작성자가 해당 룸에서 제출한 후기 내용을 함께 내려 클라이언트의
  대상별 추가 요청을 없앤다.
- Linear 첨부 화면에서는 한 룸의 여러 후기 대상을 같은 화면에서 펼쳐 작성하거나 기존 내용을
  확인한다.
- 요청 출처: Linear에 연결된 Slack 스레드
  ([#proj-moimyeon](https://team-100-thieves.slack.com/archives/C0B6VPJEZ2S/p1788436341020479)).

## PRD 근거

- 운영 LLM Wiki MCP의 `raw/product/유저-후기-및-신뢰-관리`가 현재 PRD 원문이다.
  과거 Notion URL은 이 문서의 `legacy_source_url`로만 남아 있다.
- §2: 클로징 제출 직후 후기 작성 화면으로 이어지며, 작성 후기는 제출 3시간 뒤부터 반영한다.
- §3·§4.1: 완료 룸에서 작성자와 대상자가 모두 출석했고 서로 다른 회원일 때만 작성할 수 있다.
- §4.2: 평가 태그와 텍스트 피드백은 모두 선택이고 별점은 없다.
- §4.3: 활성 후기는 룸·작성자·대상자 조합당 하나이며, 작성자와 대상자에게 제출 여부를 표시한다.
  작성자는 제출 후 3시간 동안 수정·삭제할 수 있다.
- §4.4: 대상자별 건너뛰기가 가능하고, 건너뛴 뒤에도 다시 작성 화면에 진입할 수 있다.
- §4.6의 작성자 완전 익명화 문구는 구버전이며, 현재 API의 `anonymous` 선택 노출이 최신 정책이다.
- §4.8: 제출 시점의 최신 룸 상태와 출석 기록으로 작성 가능 여부를 다시 판정한다.
- §8 1단계: 후기 대상자 목록, 평가 태그, 텍스트 피드백, 건너뛰기와 중복 방지가 기본 범위다.

## 요구사항 핵심

- `GET /v1/rooms/{roomId}/reviews/overview`는 후기 대상자 목록과 작성자가 해당 룸에 제출한
  후기 목록을 한 번에 반환한다.
- 기존 `GET /v1/rooms/{roomId}/review-targets`는 프론트 전환 기간 동안 이전 응답 계약으로
  병행 제공하고 deprecated로 문서화한다.
- 후기는 요청의 `roomId`와 로그인 작성자 id로 제한하고, 삭제되지 않은 후기만 조회한다.
- 대상별 `WRITABLE`·`SUBMITTED` 상태는 작성 후기 존재 여부에서 조립하며, `ReviewTarget`은
  작성 후기나 nullable `reviewId`를 소유하지 않는다.
- 기존 작성 진행 수와 단건 `GET /v1/reviews/{reviewId}` 동작은 유지한다.
- 선택 텍스트의 부재는 Service 경계에서 빈 문자열로 정규화한다.

## 관련 코드

- `core/core-api/src/main/kotlin/io/plady/moimyeon/core/domain/trust/ReviewTargetFinder.kt`:
  룸·출석 자격을 확인하고 후기 대상 회원을 찾는다.
- `core/core-api/src/main/kotlin/io/plady/moimyeon/core/domain/trust/WrittenReviewFinder.kt`:
  작성자 관점의 후기 조회와 `WrittenReview` 변환을 담당한다.
- `core/core-api/src/main/kotlin/io/plady/moimyeon/core/domain/trust/ReviewTarget.kt`:
  이후 후기 제출·건너뛰기에 사용하는 대상 회원을 표현한다.
- `storage/db-core/src/main/kotlin/io/plady/moimyeon/storage/db/core/ReviewRepository.kt`:
  룸·작성자 기준 후기와 지연 로딩 태그를 조회한다.
- `core/core-api/src/main/kotlin/io/plady/moimyeon/core/api/controller/v1/response/ReviewOverviewResponse.kt`:
  후기 대상·작성 상태·작성 후기를 포함한 룸별 후기 작성 개요 응답을 조립한다.
- `core/core-api/src/test/kotlin/io/plady/moimyeon/core/api/controller/v1/ReviewControllerTest.kt`:
  `reviews/overview` RestDocs 계약을 정의한다.
- `.worklog/MOI-493-get-review/`:
  작성한 후기 단건 조회의 작성자·공개 시각·숨김 정책 결정 기록이다.

## 작업 경계

- 후기 제출·수정·삭제 규칙과 3시간 수정 가능 정책은 변경하지 않는다.
- `anonymous` 수정 지원을 추가하지 않는다.
- 받은 후기 공개 조건(`visibleAt`, `hiddenAt`)을 변경하지 않는다. 작성자 자신의 후기 노출은
  MOI-493의 기존 정책을 유지한다.
- DB 스키마와 후기 단건 조회 API를 제거하거나 변경하지 않는다.
- 새로운 상위 개념이나 sealed 타입을 선제적으로 도입하지 않는다.

## 구버전 PRD 잔재

- PRD §4.6의 작성자 완전 익명화 문구는 구버전이다. 현재 제출 API와 받은 후기 응답의
  `anonymous` 선택 노출 정책이 최신이며 MOI-508도 이를 유지한다.
- PRD §6의 `기억에 남는 질문`은 후기 입력이 아니다. 「룸 진행」 §4.6의 클로징 질문 평가가
  원질문별 `MEMORABLE`·`DISAPPOINTING`을 수집하며, 현재 `ClosingService`와
  `ClosingSubmissionManager`가 이 흐름을 구현한다. 후기 PRD의 해당 문구는 삭제 대상이다.
