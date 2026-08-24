---
name: entity-design
description: PRD를 바탕으로 엔티티 모델과 테이블을 설계한다. "엔티티 모델링", "테이블 설계", "ERD 초안", "schema.sql 반영" 요청 시 사용한다. 1단(논리 모델링 — DBML 초안, 팀 회의 인풋)과 2단(합의 후 물리 모델링과 JPA 엔티티·Flyway 마이그레이션·schema.sql 갱신 PR)으로 나뉜다. API·Service·비즈니스 로직은 만들지 않는다.
---

# entity-design — 엔티티·테이블 설계

진행 규칙: `[체크포인트]`에서는 산출물을 제시하고 **턴을 끝낸다** — 사람
승인 없이 다음 단계로 가지 않는다(사용자가 "끝까지 진행해"라고 명시하면
생략). `.worklog/{이슈키}/`가 있으면 재실행이다 — plan.md 체크박스(사람이
승인한 단계에만 `[x]`)로 마지막 승인 지점을 찾아 그 다음부터 재개하고,
기존 산출물은 차분만 수정한다. 1단 합의가 끝난 상태면 2단부터 시작한다.
없으면 초기 실행이다 — .worklog 디렉토리와 plan.md(단계 체크리스트)부터 만든다.

## 1단 — 논리 모델링 (팀 회의 인풋)

개념 모델링과 논리 모델링을 한 번에 진행한다 — 산출물은 논리 모델이다.

1. **컨텍스트 수집** — `.agents/skills/issue-context/SKILL.md` 수행. PRD를
   정독한다.
2. **엔티티 도출** — `docs/knowledge/erd-design.md`의 판정 질문을 명시
   적용한다: 명사의 엔티티 승격 예외, 동사→기록 판정, 기본/중심/행위 태깅,
   M:N의 숨은 속성 질문, 스냅샷 vs 파생 값 경계.
3. **논리 모델 작성** — `.worklog/{이슈키}/erd.dbml`에 DBML로: 엔티티·관계,
   핵심 상태 필드와 전이, 유니크·존재 종속 제약. 타입은 논리 수준까지만 —
   MySQL 타입·인덱스 확정은 2단 물리 모델링의 몫이다. **미결정 목록**(정책
   판단이 필요한 것)은 `.worklog/{이슈키}/tbd.md`에 — 추측으로 채우지
   않는다. 회의에서는 DBML 렌더링(IntelliJ·VSCode 플러그인)으로 본다.
   **[체크포인트 A: 팀 합의]** — 회의 결과가 나올 때까지 2단으로 넘어가지
   않는다.

## 2단 — 물리 모델링·합의 반영 (PR)

4. **worktree 준비** — `docs/conventions/git.md`.
5. **물리 모델링** — 합의된 논리 모델을 MySQL 설계로 확정한다: 컬럼
   타입·길이·널 허용, 인덱스, 제약. `docs/conventions/storage.md` 전체
   (베이스 엔티티, soft delete, 유니크 매핑, schema.sql 규칙)를 읽고 따른다.
6. **구현** — JPA 엔티티 + Flyway 마이그레이션 + schema.sql 갱신으로
   반영한다. 물리 스키마의 단일 소스는 schema.sql이다 — erd.dbml은 이슈
   단위 논리 설계 기록으로 남기고 이후 갱신 의무를 지지 않는다.
7. **리뷰** — `.agents/agents/db-reviewer.md` 위임 (마이그레이션·엔티티 파일
   + review-diff.patch + context.md 경로 — 위임 전 diff를 `.worklog/{이슈키}/review-diff.patch`로 저장해 함께 준다.) ddl_analysis 판정 포함. 반영 상한 2회 — 소진하면
   plan.md에 사유를 기록하고 턴을 끝낸다.
8. **검증** — `./gradlew test ktlintCheck` 통과 (Flyway 버전 중복은 CI가
   차단). 실패 수정 상한 3회 — 소진 시 기록 후 턴 종료.
   **[체크포인트 B: 구현 승인]**
9. **커밋·PR** — `.agents/skills/ship-pr/SKILL.md`를 수행한다. 합의에서
   달라진 점이 있으면 decisions.md에 기록.

## 하지 않는 것

- API·Service·화면 로직 — 스키마와 엔티티 매핑까지다.
- 요구에 없는 이력 테이블·통계 테이블·인덱스의 선제 도입 — 필요 신호가
  있을 때 erd-design.md의 도입 판정을 거친다.
