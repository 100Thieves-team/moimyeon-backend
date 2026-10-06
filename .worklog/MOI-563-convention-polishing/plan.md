# MOI-563 컨벤션 polishing 계획

## 단계

- [x] A. 범위 결정 (2026-10-06)
  - 이름 규칙: 역할 단어 하나(`service`, `manager` …)만 금지, `accessValidator` 같은 줄임은 허용
  - 주석 정리: main 코드 전체
- [x] B. 컨벤션 문서·리뷰 에이전트에 규칙 추가
- [x] C. 기존 코드 정렬 (`./gradlew test ktlintCheck` 통과)
  - qa-reviewer: PASS, 권고 5건 반영(안전장치 주석 3곳 복원, 잘못 붙은 CONFIRMED 주석을 `countAtRoomConfirmation` 위로 이동, 로깅 `writer` 매개변수 이름 정렬)
  - 같은 규칙을 따로 구현한 곳은 한 곳을 가리키는 참조를 남긴다는 문장을 주석 규칙에 추가
- [x] D. 커밋·PR (base: dev)

## 접근

- `kotlin-style.md`에 "컴포넌트 이름" 절을 만들고 주석 절에 금지 항목(컨벤션으로 알 수 있는 사실,
  여러 파일 반복, RestDocs와 겹치는 DTO 주석)을 더한다. README 요약과 `code-reviewer` 점검 항목에도 반영한다.
- 이름 변경: 이 레포가 정의한 컴포넌트를 담는 필드·변수만 타입명 camelCase로 바꾼다.
  라이브러리 객체, enum 값, 값 객체(`NotificationRetryPolicy`), 여러 Repository를 받는
  제네릭 헬퍼(`ProfileInterestManager.replace`)의 매개변수는 대상이 아니다.
- 주석 정리: 코드 외 변경 없이 주석만 지우거나 다듬는다. 같은 이유가 여러 곳에 있으면
  규칙이 실제로 걸리는 한 곳에만 남긴다.

## PR 분할

변경 파일이 50개 상한을 넘어 서로 의존하지 않는 PR로 나눈다. 이름 변경 파일과 주석 정리 파일은 겹치지 않는다.

| PR | 범위 |
| --- | --- |
| 이름 1 | 컨벤션 문서·하네스, worklog, core-api main(Notification 설정·웹 푸시), domain 테스트(catalog~question) |
| 이름 2 | domain 테스트(resume~trust), event 테스트 |
| 이름 3 | core-api 로깅(main)과 나머지 테스트, 다른 모듈 |
| 주석 1 | core-api api 레이어·공통, security·admin·clients |
| 주석 2 | core-api domain, storage |

## 정리 중 발견, 이번 범위 밖

- 내용이 틀렸을 수 있는 주석: `ParticipationRepository.findByMemberIdAndRoomIdIn`의 "기준은 탐색 목록의 집계와 같다",
  `RoomFinder`의 "합칠지는 MOI-330 PR 리뷰에서 정한다"
- `storage.md`가 요구하는데 빠진 주석: `RefreshTokenEntity`(베이스 미상속 이유), `RoomStatusLogEntity`(append-only)
- 벌크 UPDATE에서 `updatedAt`을 직접 넣는 이유가 Repository 3곳에 반복됨. `docs/knowledge/`로 모을 후보
