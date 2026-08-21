# MOI-474 — 미결정 사항

- `entity-design`을 독립 스킬로 유지할지: 절차가 짧아 스킬 가치가 불확실.
  Step 3에서 작성 후 With/Without 비교로 결정한다.
  - 사람이 step1에서 단 의견: 필요하다면 ERD 설계 도메인 지식 문서를 줄 수 있어. 필요하다면 말해.
  - Codex 검토(2026-08-21) 추가 쟁점: 산출물이 "schema.sql 초안"이면
    DR-011 ①(독립 머지 가능) 테스트를 통과하지 못하고 Flyway-only 규칙과도
    충돌한다. Step 3에서 산출물을 "엔티티+마이그레이션+schema.sql 갱신 PR"로
    재정의할지, 회의용 초안 단계로 재분류할지 결정.
- `issue-context`·`ship-pr`을 스킬로 둘지 공유 reference 문서로 둘지:
  단독 호출 가치 기준으로 Step 2·4에서 확정 (DR-008).
- Codex용 커스텀 에이전트 정의(`.codex/agents/*.toml`)가 별도로 필요한지:
  `code-reviewer` 작성 시(Step 2) 판단 (DR-002 범위 한계).
- DR-004·008은 승인(잠정): Step 6에서 트리거 양성/음성/경계 프롬프트로
  레포 기준 측정 후 확정.
- AGENTS.md 라우팅·포인터의 구체 문안: Step 6에서 확정.
- worklog GC의 구체 주기·조건: 운영 경험이 쌓인 뒤 결정 (DR-006).
