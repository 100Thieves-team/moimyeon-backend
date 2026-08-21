# MOI-474 하네스 구축 — 계획

설계-검토-구현 사이클로 진행한다. 각 스텝이 체크포인트다(승인 후 다음 스텝).
설계 근거는 [decisions.md](decisions.md), 미결정 사항은 [tbd.md](tbd.md).

- [x] Step 1 — 골격: `.agents/` 단일 소스 + symlink 3개, worklog 파일 계약,
      의사결정 기록, 아키텍처 도면. 양 런타임 스킬 발견 검증 (2026-08-19 승인·커밋)
- [ ] Step 2 — `requirement-implementation` + `issue-context` 스킬,
      `.agents/execution-policy.md`(공유 실행 정책), `code-reviewer` 에이전트,
      eval 러너 + requirement-implementation eval 세트 (DR-012 스킬의 TDD)
- [ ] Step 3 — `api-spec-definition` · `api-connection` · `entity-design` 스킬
- [ ] Step 4 — `ship-pr` 스킬 및 공통부 정리
- [ ] Step 5 — 결정론 게이트: 변경 파일 대응 테스트·ktlint 훅
- [ ] Step 6 — 진화 루프(랩업→레슨→승격), AGENTS.md 라우팅·포인터 등록,
      트리거 양성/음성 검증 + With/Without 1회, 완공 정리(도면 Step 라벨 제거,
      유효 결정의 knowledge 승격)
