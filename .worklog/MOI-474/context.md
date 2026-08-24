# MOI-474 — 컨텍스트

- 목표: 이 레포에 Claude Code 중심(코덱스 호환) 팀 하네스를 설계-검토-구현
  사이클로 구축한다.
- 참고 자료: `_workspace/harness-reference/` (gitignore — 로컬 전용).
  하네스 엔지니어링 책 요약 12장, 3주 워크플로우 세션 분석(2026-08, 상위 세션 42개),
  실험 레퍼런스(Vercel·LangChain·arXiv), 현업 멘토링 녹취(2026-08-19).

## 근거 색인 (decisions.md 인용의 검증 경로)

**외부 실험·논문 (팀원 누구나 재검증 가능):**

| 인용 | 출처 |
| --- | --- |
| AGENTS.md 인덱스 vs 스킬 (100%/53%, 미호출 56%, 40→8KB) | vercel.com/blog/agents-md-outperforms-skills-in-our-agent-evals (2026-01) |
| 스킬 평가·오선택 (9%→82%, 유사 스킬 20→12개, 명시 지시 ~70%) | langchain.com/blog/evaluating-skills |
| supervisor vs swarm, distractor 도메인 급락 | langchain.com/blog/benchmarking-multi-agent-architectures |
| 컨텍스트 파일 효과 (±3~4%, 비용 +10~22%) | arXiv:2602.11988 "Evaluating AGENTS.md" |
| 오케스트레이터-워커 +90.2%·토큰 15배 (병렬 리서치 한정) | anthropic.com/engineering/multi-agent-research-system |
| 멀티에이전트 실패 분류 (~79%가 명세·정렬) | arXiv:2503.13657 (MAST — 판본별 수치 차이 있음) |
| LLM 라우터 정확도 90%+·홉당 600~800ms | arXiv:2412.05449 (Amazon) |
| 과제 길이-성공률, 50% 지평 ~50분 | arXiv:2503.14499 (METR, 2025) |
| p^n 복리·자기조건화 | arXiv:2509.09677 |
| 도구 수 vs 선택 정확도 | arXiv:2505.10570 (LongFuncEval), arXiv:2605.24660 (Meta) |
| 장문·무관 컨텍스트 저하 | arXiv:2307.03172 (Lost in the Middle) |

**자체 실험 (재현 조건 명시):**

- Codex 스킬 발견 검증(DR-002): Codex CLI 0.149.0, macOS, 임시 스킬을
  `.agents/skills/harness-smoke/`에 두고 `codex exec --sandbox read-only`로
  스킬 목록 질의. symlink 제거/복원 두 조건 모두 발견 1회 — 2026-08-21.

**비재현 관찰 (참고용 — 재검증 불가):**

- 멘토링 녹취(2026-08-19): plan-first 워크플로우, 게이트 훅, 지식 승격 체계
  등. 녹취 원본과 주제별 재구성 노트(harness_mentoring_note.md)는 로컬
  `_workspace/`에만 있다.
- 3주 세션 분석(상위 세션 42개, update_plan 358회 등): 개인 로컬
  `~/.codex` trace 집계. 집계 스크립트는 `_workspace/harness-reference/
  workflow_analysis/`에 있으나 원 데이터가 개인 로컬이라 팀 재현은 불가.
- Seleznov 650회 실측(스킬 활성화 ~50%): 원문 접근 불가(403), 검색 요약
  기반 참고치.
- 대상 워크플로우 4종: 요구사항 구현(service TDD) / API 스펙 정의(+모킹) /
  API 연결 / 엔티티·테이블 설계.
- 제품 방향(2026-08-21): 피봇 후 현 제품에 Bedrock 기반 이력서 요약이 있고,
  이력서 기반 모의 면접 질문 생성 등 AI 기능이 계속 추가될 예정 —
  LLM 관련 하네스 결정(DR-014)의 배경.
- 제약: session team 프리미티브 미사용, 개인 스킬(drawio 등) 팀 하네스 포함 금지,
  docs/conventions 내용 유실·변경 금지.
