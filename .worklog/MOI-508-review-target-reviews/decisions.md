# MOI-508 결정 기록

## 개념은 유즈케이스에서 반드시 함께 움직이는 데이터로 판정한다

- 날짜: 2026-09-03
- 내용과 이유: API DTO나 JPA Entity 모양, 단순 메서드 파라미터·반환 여부만으로 개념을 만들지 않는다.
  반환된 뒤 다음 행동에 쓰이고, 분리하면 유즈케이스의 의미가 깨지는 데이터만 개념으로 유지한다.
- 대안: 모든 입력 Content·Command·조회 컨테이너를 개념으로 분류하는 안은 포장 타입과 이름만 늘려 폐기했다.

## ReviewTarget과 WrittenReview를 소유 관계로 묶지 않는다

- 날짜: 2026-09-03
- 내용과 이유: ReviewTarget은 작성·건너뛰기에 쓰는 대상 회원이고 WrittenReview는 수정·삭제·프리필에
  쓰는 작성 후기다. 둘은 `memberId`와 `targetMemberId`로 연결하지만 서로를 소유하지 않는다.
- 대안: nullable 후기 필드, sealed 상태 계층, ReviewEntry 상위 개념은 현재 반복 규칙이 없어 도입하지 않는다.

## 선택 후기 텍스트는 Service 경계에서 빈 문자열로 정규화한다

- 날짜: 2026-09-03
- 내용과 이유: PRD에서 텍스트 피드백은 선택이지만 없음과 빈 문자열을 다르게 처리하는 유즈케이스가 없다.
  저장된 null은 조회 변환 시 빈 문자열로 바꿔 상위 레이어의 null 분기를 제거한다.
- 대안: 저장 계층까지 즉시 non-null로 바꾸는 안은 기존 데이터와 스키마 마이그레이션을 요구해 제외한다.

## 룸별 작성 후기는 기존 인덱스로 fetch join한다

- 날짜: 2026-09-03
- 내용과 이유: MySQL 8 `EXPLAIN FORMAT=TREE`에서 후기 조회는
  `uk_review_room_author_target_active`의 `(room_id, author_member_id)` 왼쪽 접두어를 사용하고,
  태그 조인은 `uk_review_tag(review_id, tag)`의 covering index를 사용한다. Hibernate 통합 테스트로
  여러 후기와 태그를 도메인 객체로 변환하는 조회 SQL이 1회임을 고정했다.
- 대안: `deleted_at`과 정렬까지 포괄하는 추가 인덱스는 룸당 후기가 최대 7건이라 비용 대비 근거가 없어 제외했다.

검증 SQL은 식별자를 임의의 이진 값으로 치환해 실행했다.

```sql
EXPLAIN FORMAT=TREE
SELECT DISTINCT r.*
FROM review r
LEFT JOIN review_tag t ON t.review_id = r.id
WHERE r.room_id = X'01920000000070008000000000000457'
  AND r.author_member_id = X'00000000000000000000000000000001'
  AND r.deleted_at IS NULL
ORDER BY r.id ASC;
```

주요 실행 계획:

```text
Nested loop left join
├─ Index lookup on r using uk_review_room_author_target_active
│  (room_id=..., author_member_id=...)
└─ Covering index lookup on t using uk_review_tag (review_id=r.id)
```

기존 단건 경로도 비교했다. 후기는 PK 조회, 태그는 `uk_review_tag(review_id, tag)` 조회를 사용하지만
대상 수만큼 두 조회가 반복된다. 신규 경로는 Hibernate statement count 테스트에서 태그를 포함해 SQL 1회다.

## anonymous 선택 노출을 최신 후기 정책으로 따른다

- 날짜: 2026-09-03
- 내용과 이유: PRD §4.6의 작성자 완전 익명화는 구버전이며, 현재 API의 `anonymous` 선택과
  비익명 후기 작성자 닉네임 노출이 최신 정책이라는 제품 결정을 확인했다. MOI-508은 이 계약을 유지한다.
- 대안: 구버전 PRD에 맞춰 모든 후기를 익명화하는 변경은 현재 제품 동작을 되돌리므로 제외한다.

## 기억에 남는 질문은 클로징 질문 평가가 소유한다

- 날짜: 2026-09-03
- 내용과 이유: 기억에 남음·아쉬움은 후기 입력이 아니라 「룸 진행」 §4.6의 클로징에서
  면접자가 받은 원질문마다 `MEMORABLE`·`DISAPPOINTING`으로 제출한다. 현재
  `ClosingSubmissionManager`가 `QuestionVoteEntity`로 저장하므로 Review에 추가하지 않는다.
- 대안: 후기 데이터에 기억에 남는 질문을 추가하는 안은 동일 사실의 소유권을 두 군데로 나누므로 제외한다.

## 대상 조회와 후기 개요 조립의 이름을 분리한다

- 날짜: 2026-09-04
- 내용과 이유: `ReviewService.getTargets`는 실제 후기 대상만 반환하므로 유지한다. 반면 Facade와 API는
  `ReviewTarget`·`WrittenReview`·회원 닉네임을 조립하므로 각각 `ReviewFacade.getOverview`,
  `ReviewOverviewResponse`, `GET /v1/rooms/{roomId}/reviews/overview`로 이름을 맞춘다.
- 대안: `review-targets`와 `getTargets`를 유지하면 응답이 대상 목록뿐이라는 잘못된 계약을 전달하고,
  `review-writing`은 최초 작성과 수정 흐름을 구분하는 것처럼 읽혀 제외했다.

## 기존 후기 대상 API를 deprecated 호환 계층으로 병행 제공한다

- 날짜: 2026-09-04
- 내용과 이유: 같은 v1에서 URI와 응답 계약을 즉시 교체하면 구·신 프론트가 공존하는 배포 구간에
  한쪽이 실패한다. `reviews/overview`를 추가하고 기존 `review-targets`는 이전 응답 계약 그대로
  유지하며 OpenAPI에 deprecated로 표시한다. 프론트 전환과 잔존 호출 확인 후 별도 배포에서 제거한다.
- 대안: 동시 교체는 캐시된 웹 클라이언트의 원자적 전환을 보장할 수 없어 제외했다.
