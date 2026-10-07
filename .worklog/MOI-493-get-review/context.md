# MOI-493 — 리뷰 ID로 작성된 면접 후기 조회 API 추가

## 이슈 요약

- [MOI-493](https://linear.app/100-thieves/issue/MOI-493) (Backlog, 이중곤 생성)
- 완료된 면접 참여자 간 후기를 표시하려면 기존에 작성된 후기를 불러와야 한다.
  현재는 작성한 후기를 조회하는 API가 없어, 저장(수정) 화면 진입 시 새로 입력한
  내용으로 교체되는 문제가 있다.
- 리뷰 ID로 작성된 리뷰 데이터를 조회할 수 있는 API가 필요하다.
- 출처: Slack 스레드(2wndrhs → dbwp031, "완료된 면접의 참여자끼리 후기") 첨부.

## 현재 코드 상태

- `GET /v1/rooms/{roomId}/review-targets` 가 대상별 `reviewId`(SUBMITTED 시)를 내려준다
  (`ReviewTargetsResponse`). 클라이언트는 이 id로 후기 본문을 조회할 방법이 없다.
- `PUT /v1/reviews/{reviewId}` / `DELETE /v1/reviews/{reviewId}` 는 이미 존재
  (`ReviewController` → `ReviewService` → `ReviewEditor`). 조회(GET)만 없다.
- 받은 후기 조회는 `GET /v1/members/me/received-reviews` (공개 시각 지난 것만, 작성자 관점 아님).
- 에러: `REVIEW_NOT_FOUND`(E2006), `REVIEW_FORBIDDEN`(E2007) 재사용 가능. 새 코드 불필요.

## 관련 코드 위치

- 컨트롤러: `core/core-api/.../controller/v1/ReviewController.kt`
- 서비스: `core/core-api/.../domain/trust/ReviewService.kt`
- 조회 Implement 선례: `ReceivedReviewFinder`, `ReviewTargetFinder`
- 저장소: `storage/db-core/.../ReviewRepository.kt`, `ReviewEntity.kt`
- RestDocs: `ReviewControllerTest.kt`
