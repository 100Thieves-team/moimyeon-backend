# .agents — 하네스 단일 소스

이 디렉토리가 코딩 에이전트 하네스의 단일 소스다. 런타임 디렉토리는 전부 symlink다:
`.claude/skills`·`.codex/skills` → `skills/`, `.claude/agents` → `agents/`.
수정은 반드시 여기서 한다.

| 위치 | 책임 |
| --- | --- |
| `skills/` | 어떻게: 워크플로우별 절차. 각 본문의 체크리스트가 그 워크플로우의 오케스트레이터다 (Step 2~ 예정) |
| `execution-policy.md` | 공유 실행 정책: 체크포인트, 재시도 상한·에스컬레이트, 재실행 분기 (Step 2 예정) |
| `agents/` | 누가: 역할 계약 (Step 2~ 예정) |

라우팅은 `AGENTS.md`의 작업 유형 → 스킬 표가 담당한다. 별도 오케스트레이터 스킬은 없다.

작업 산출물 계약은 [worklog/README.md](../worklog/README.md),
구축 의사결정은 [worklog/MOI-474/decisions.md](../worklog/MOI-474/decisions.md) 참조.

![하네스 아키텍처](harness-architecture.drawio.svg)

도면은 XML 임베디드 SVG다 — draw.io에서 열면 그대로 편집된다.
