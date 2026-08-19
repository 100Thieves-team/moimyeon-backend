# MOI-474 — 컨텍스트

- 목표: 이 레포에 Claude Code 중심(코덱스 호환) 팀 하네스를 설계-검토-구현
  사이클로 구축한다.
- 참고 자료: `_workspace/harness-reference/` (gitignore — 로컬 전용).
  하네스 엔지니어링 책 요약 12장, 3주 워크플로우 세션 분석(2026-08, 상위 세션 42개),
  실험 레퍼런스(Vercel·LangChain·arXiv), 현업 멘토링 녹취(2026-08-19).
- 대상 워크플로우 4종: 요구사항 구현(service TDD) / API 스펙 정의(+모킹) /
  API 연결 / 엔티티·테이블 설계.
- 제약: session team 프리미티브 미사용, 개인 스킬(drawio 등) 팀 하네스 포함 금지,
  docs/conventions 내용 유실·변경 금지.
