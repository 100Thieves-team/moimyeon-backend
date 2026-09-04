# MOI-508 구현 계획

검증한 커밋: 미정

## 자연어 흐름

완료된 룸의 출석자는 자신을 제외한 다른 출석자를 후기 대상으로 조회한다. 같은 요청에서
작성자가 해당 룸에 제출한 활성 후기를 태그까지 일괄 조회한다. 대상별 제출 상태는 두 결과의
`memberId`와 `targetMemberId`를 비교해 조립하며, 기존 후기 텍스트가 없으면 빈 문자열로 반환한다.
모든 과정은 읽기이며 실패해도 상태 변경은 없다.

## 개념 리팩터링

- `ReviewTarget`: 이후 후기 제출·건너뛰기에 사용하는 대상 회원만 표현한다.
- `WrittenReview`: 작성자가 조회한 뒤 수정·삭제하거나 폼을 채우는 데 함께 필요한 후기 데이터를
  표현한다. 단건 조회와 룸별 목록 조회에서 같은 의미로 재사용한다.
- `ReviewTargetStatus`: 작성 후기 존재 여부에서 파생되는 상태로, `ReviewTarget`이 소유하지 않는다.
- `ReviewSubmissionContent`·`ReviewUpdateContent`·`ReviewSkipContent`와 Command들은 유즈케이스 입력 및
  실행 문맥을 운반하는 객체이지 핵심 개념으로 취급하지 않는다.
- `ReviewEntity`의 nullable 저장 표현은 기존 데이터 호환을 위해 유지하되, Service 경계 위의
  후기 텍스트는 `String`으로 정규화한다.
- `ReviewEntry`, `ReviewWriting`, `ReviewForm`, 범용 `Review`, sealed 계층은 만들지 않는다.

## 구현 접근

- `ReviewTarget`에서 `status`와 nullable `reviewId`를 제거한다.
- `ReviewTargetFinder`는 룸·출석 자격과 대상자 판정만 담당하고 `ReviewRepository` 의존을 제거한다.
- `WrittenReviewFinder`에 `roomId + authorMemberId` 기준 활성 후기 목록 조회를 추가한다.
- 룸별 작성 후기 조회는 `tags`를 일괄 fetch해 대상 수에 따른 DB 추가 조회를 만들지 않는다.
- `WrittenReview`와 `ReceivedReview`의 `content`는 non-null `String`으로 바꾸고, 저장된 null은
  변환 시 `orEmpty()`로 정규화한다. 제출·수정 입력도 API 경계를 지난 뒤 빈 문자열을 사용한다.
- `ReviewFacade.getOverview`가 대상자·작성 후기·닉네임을 조립하고 대상별 상태와 작성 수를 계산한다.
- `ReviewOverviewResponse`에 non-null `reviews` 배열을 추가하고 `targets[].reviewId`는 제거한다.
  작성 후기의 `targetMemberId`로 대상과 연결한다.
- 기존 단건 `GET /v1/reviews/{reviewId}`는 유지하되 `content` 계약을 non-null 문자열로 맞춘다.

## 테스트 목록

- `ReviewTargetFinderTest`: 완료 룸 출석자 중 본인과 결석자를 제외한 대상만 반환하고 후기 저장소를
  사용하지 않는다.
- `WrittenReviewFinderTest`: 룸·작성자로 활성 후기 목록을 제한하고 id·대상·태그·텍스트·익명 여부를
  함께 반환한다.
- 영속성 통합 테스트: 여러 후기의 태그를 누락 없이 일괄 조회하고 다른 룸·작성자·삭제 후기를 제외한다.
- 텍스트 정규화 테스트: 저장된 null 후기 텍스트를 `""`로 반환하며 태그가 없으면 빈 Set/List를 반환한다.
- `ReviewServiceTest`: 대상 목록과 룸별 작성 후기 목록 조회를 각각 위임한다.
- `ReviewFacadeTest`: 대상과 후기를 `targetMemberId`로 연결해 `WRITABLE`·`SUBMITTED` 상태와 제출 수를
  계산하고 후기 목록을 응답으로 조립한다.
- `ReviewControllerTest` RestDocs: `targets[].reviewId` 제거, `data.reviews[]` 추가 및 non-null content를
  문서화한다.
- 기존 제출·수정·삭제·단건 작성 후기·받은 후기 테스트가 그대로 통과한다.

## API 문서와 외부 소비자

- 실제 제공 정보를 드러내는 `GET /v1/rooms/{roomId}/reviews/overview`를 추가한다.
- 기존 `GET /v1/rooms/{roomId}/review-targets`는 이전 응답 계약을 유지한 deprecated 호환 API로
  병행 제공하고, 프론트 전환과 잔존 호출 확인 후 별도 배포에서 제거한다.
- `targets[].reviewId`를 제거하고 `reviews[]`를 추가하므로 프론트 SDK 재생성과 동시 전환이 필요하다.
- 프론트는 `target.memberId == review.targetMemberId`로 기존 후기를 연결하며 대상별 단건 조회를 제거한다.
- RestDocs와 생성 OpenAPI를 갱신한다.

## 영향 범위

- 후기 조회 개념·Finder·Facade·응답 DTO와 관련 테스트를 리팩터링한다.
- DB 스키마, 후기 제출·수정·삭제 규칙, 3시간 공개 기준, 신고·신뢰 집계 정책은 변경하지 않는다.
- 룸 최대 인원은 8명이므로 한 응답에 포함되는 작성 후기는 최대 7건이다.

## 단계

- [x] 계획 승인 (2026-09-03: MOI-508 범위를 개념 리팩터링까지 확장해 진행)
- [x] 서비스 테스트 스펙 승인 (2026-09-03: 사용자 "진행해")
  - 구현 전 예상 컴파일 실패(`getWrittenReviews`, ReviewTarget 책임 분리) 확인
- [x] TDD 구현
  - 구현 완료: 대상자 책임 분리, 룸별 작성 후기 조회, non-null content 정규화, 응답 계약 갱신
  - 이름 정리: `ReviewFacade.getOverview`, `ReviewOverviewResponse`, `reviews/overview`로 조립 조회임을 명시
  - 배포 호환: `review-targets`는 기존 응답 계약을 유지하고 OpenAPI deprecated로 병행 제공
- [x] 코드·DB 리뷰
  - 1차 권장 2건 반영: 룸별 작성 후기 전체 보존, fetch join SQL 1회 검증
  - 재검토: code-reviewer 통과, db-reviewer 통과
  - 이름·URI 변경 재검토: code-reviewer 필수·권장 지적 없이 통과
  - PR 전 QA 1차: API 병행 제공 필수, 공개 전 작성 후기 회귀 테스트 권고
- [x] `./gradlew test ktlintCheck` 검증
  - 이름·URI 변경 후 `./gradlew test ktlintCheck restDocsTest` 통과
- [x] 구현 승인 (2026-09-04: 사용자 승인)
- [ ] 커밋·PR
