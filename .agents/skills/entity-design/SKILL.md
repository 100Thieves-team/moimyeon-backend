---
name: entity-design
description: PRD를 바탕으로 엔티티 모델과 테이블을 설계한다. "엔티티 모델링", "테이블 설계", "ERD 초안", "schema.sql 반영" 요청 시 사용한다. 1단(설계 초안 — 팀 회의 인풋)과 2단(합의 후 JPA 엔티티·Flyway 마이그레이션·schema.sql 갱신 PR)으로 나뉜다. API·Service·비즈니스 로직은 만들지 않는다.
---

# entity-design — 엔티티·테이블 설계

[execution-policy.md](../../execution-policy.md)·[security-policy.md](../../security-policy.md)를
따른다. `worklog/{이슈키}/`가 있으면 재실행 분기부터 — 1단 합의가 끝난
상태면 2단부터 시작한다.

## 1단 — 설계 초안 (팀 회의 인풋)

1. **컨텍스트 수집** — `.agents/skills/issue-context/SKILL.md` 수행. PRD를
   정독한다.
2. **엔티티 도출** — `docs/knowledge/erd-design.md`의 판정 질문을 명시
   적용한다: 명사의 엔티티 승격 예외, 동사→기록 판정, 기본/중심/행위 태깅,
   M:N의 숨은 속성 질문, 스냅샷 vs 파생 값 경계.
3. **초안 작성** — `worklog/{이슈키}/`에: 엔티티 관계(텍스트 또는 mermaid),
   핵심 상태 필드와 전이, 유니크·존재 종속 제약, **미결정 목록**(정책 판단이
   필요한 것 — 추측으로 채우지 않는다).
   **[체크포인트 A: 팀 합의]** — 회의 결과가 나올 때까지 2단으로 넘어가지
   않는다.

## 2단 — 합의 반영 (PR)

4. **worktree 준비** — `docs/conventions/git.md`.
5. **구현** — 합의된 설계를 JPA 엔티티 + Flyway 마이그레이션 + schema.sql
   갱신으로 반영한다. `docs/conventions/storage.md` 전체(베이스 엔티티,
   soft delete, 유니크 매핑, schema.sql 규칙)를 읽고 따른다.
6. **리뷰** — `.agents/agents/db-reviewer.md` 위임 (마이그레이션·엔티티 파일
   + context.md 경로). ddl_analysis 판정 포함. 반영 상한 2회.
7. **검증** — `./gradlew test ktlintCheck` 통과 (Flyway 버전 중복은 CI가
   차단).
   **[체크포인트 B: 구현 승인]**
8. **커밋·PR** — 합의에서 달라진 점이 있으면 decisions.md에 기록.

## 하지 않는 것

- API·Service·화면 로직 — 스키마와 엔티티 매핑까지다.
- 요구에 없는 이력 테이블·통계 테이블·인덱스의 선제 도입 — 필요 신호가
  있을 때 erd-design.md의 도입 판정을 거친다.
