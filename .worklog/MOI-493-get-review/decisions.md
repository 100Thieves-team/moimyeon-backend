# MOI-493 결정 기록

## D1. 조회 주체는 작성자 본인만

- 이 API의 용도는 수정 화면 진입 시 기존 작성 내용을 채우는 것이다(이슈 본문).
  대상자가 받은 후기를 보는 경로는 `GET /v1/members/me/received-reviews`가 이미 있다.
- 따라서 작성자 불일치는 `REVIEW_FORBIDDEN`(E2007)으로 막는다. 기존 PUT/DELETE와 같은 판정.

## D2. 공개 기준 시각(visibleAt)·숨김(hiddenAt)과 무관하게 조회 허용

- 조회는 상태를 바꾸지 않고, 작성자가 자기 글을 보는 것이므로 공개 시각 판정을 하지 않는다.
  수정 창 판정(E2008)은 기존 PUT/DELETE의 몫으로 남긴다.
- hiddenAt도 걸지 않는다: 기존 수정·삭제 경로(`findForUpdateByIdAndDeletedAtIsNull`)가
  hiddenAt을 보지 않는 것과 정합을 맞췄다. 숨김 후기의 작성자 노출 정책이 별도로 정해지면
  쓰기 경로와 함께 재검토한다 (tbd.md).

## D3. Facade 없이 Controller → Service 직행

- 닉네임 등 다른 도메인 조합이 없다(대상 닉네임은 review-targets API가 이미 내려준다).
  layers.md 기준 Facade는 조합이 필요할 때만 둔다. 기존 update/delete 핸들러와 같은 구조.

## D4. 새 Implement `WrittenReviewFinder` 분리

- ReviewEditor는 쓰기(락 조회) 도구라 읽기를 섞지 않았다. ReceivedReviewFinder(받은 후기),
  ReviewTargetFinder(작성 대상)처럼 조회 관점별 Finder를 두는 기존 패턴을 따랐다.
- 태그는 단건 조회이므로 fetch join 없이 트랜잭션 안에서 지연 로딩한다.
