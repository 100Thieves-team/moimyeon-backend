# 컨텍스트

[MOI-517](https://linear.app/100-thieves/issue/MOI-517)과
[프론트 PR #30 리뷰](https://github.com/100Thieves-team/moimyeon-frontend/pull/30#discussion_r3964574617)의
받은 후기 조회 API 계약 불일치를 수정한다. 화면은 마이페이지의 받은 후기 첫 조회와 더 보기다.
근거는 사용자와 합의한 AS-IS/TO-BE, 기존 서버 DTO, REST Docs 테스트 및 게시된 dev OpenAPI다.

- `ReceivedReviewsRequest.kt`: 두 값은 nullable. 커서 생략은 첫 페이지, size 생략·범위 밖은 20.
- `ReviewControllerTest.kt`: 정상/기본값/E400 문서화 사례의 optional과 설명이 달라 병합 스펙에서 필수가 됨.
- `core/core-api/build.gradle.kts`: REST Docs에서 OpenAPI를 생성하고 파서로 검증.
- 프론트 `received-reviews.tsx`: size=5만 전달하는 첫 요청에서 생성 타입을 단언으로 우회.

이번 PR 범위는 백엔드 문서 계약과 검증이다. 서버 런타임·DB·인프라 변경은 없다.
프론트는 dev OpenAPI 게시 후 SDK 재생성과 타입 단언 제거가 후속으로 필요하다.
