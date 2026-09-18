# MOI-493 구현 계획

사용자 지시: "이슈 보고 개발 및 커밋, PR까지" (끝까지 진행 요청으로 체크포인트 생략)

## 접근 방식

작성자 본인이 자신이 작성한 후기 한 건을 리뷰 id로 조회하는 GET API를 추가한다.
수정 화면 진입 시 기존 태그·텍스트·익명 여부를 채우는 용도이므로 **작성자만**
조회할 수 있고, 공개 시각(visibleAt)과 무관하게 조회를 허용한다(수정 창 판정은
기존 PUT의 몫).

- URI: `GET /v1/reviews/{reviewId}` (기존 PUT/DELETE와 같은 리소스 경로)
- 응답: reviewId, roomId, targetMemberId, tags, content, anonymous
- 에러: 미인증 E1102, 활성 후기 없음 E2006(REVIEW_NOT_FOUND),
  작성자 불일치 E2007(REVIEW_FORBIDDEN). 새 에러 코드 없음.

## 변경 지점

- `storage/db-core/ReviewRepository.kt`: `findByIdAndDeletedAtIsNull` 추가
- `core.domain.trust`: `WrittenReview`(도메인 결과), `WrittenReviewFinder`(조회 Implement)
- `core.domain.trust.ReviewService`: `getWrittenReview(authorMemberId, reviewId)` 추가
- `core.api.controller.v1.ReviewController`: GET 핸들러 추가 (Facade 불필요 — 조합 없음)
- `core.api.controller.v1.response.WrittenReviewResponse` 추가

## 테스트

- `WrittenReviewFinderTest` (unit): 정상 조회 / 없음·삭제 E2006 / 작성자 불일치 E2007 /
  공개 시각 이후에도 조회 허용
- `ReviewServiceTest`: finder 위임
- `ReviewControllerTest` (RestDocs): 정상 조회 문서화 / 미인증 E1102 / 도메인 오류(E2006·E2007)

## API 문서 영향

RestDocs → openapi3.yaml에 `getReview` 오퍼레이션 추가. 외부 소비자: 프론트(후기 수정 화면).

## 단계

- [ ] 계획 (체크포인트 A — 사용자 "끝까지" 지시로 생략)
- [ ] 테스트 스펙 (체크포인트 B — 생략)
- [ ] TDD 구현 — 완료 (2026-08-31)
- [ ] 리뷰 — 완료: code-reviewer 필수 0·권장 1(태그 정렬, 반영),
  qa-reviewer PASS·권고 2(숨김 정책 테스트 반영, 404/403 열거는 기존과 동일해 조치 불요)
- [ ] 검증 — `./gradlew test ktlintCheck restDocsTest` 통과
- [ ] 커밋·PR — feat(trust) d45fc2b0 / feat(api) 93808a3a, PR 생성

검증한 커밋: 93808a3a
