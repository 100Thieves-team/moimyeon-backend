# MOI-474 — 미결정 사항

- 전문 에이전트 블루프린트 소재 매핑 (로컬
  `_workspace/harness-reference/specialist-agents-blueprint.md`, 원전 링크
  29개 포함 — DR-015 소싱 소스. 해당 스텝 작성 시 벤더링):
  - Step 3 `db-reviewer`: SQL 리뷰 10단계 절차 + 검사항목, DDL 분석 출력
    스키마(INSTANT/INPLACE/COPY·lock·rollback — "ALTER니까 online 추정 금지",
    MySQL 원전), expand-contract 8단계 상세
  - Step 4 `qa-reviewer`: 변경 영향 분류, 위험 신호 목록, 특수 검증
    8문항(retry 중복·타인 ID 접근(BOLA/BFLA, OWASP 원전)·nullable 양버전
    호환·pagination 안정성·timeout×retry 곱)
  - Step 4b `incident-response`: MySQL 장애 진단 순서 + 완화 사다리(낮은
    위험부터), SRE 완화 사다리(flag off→…→직접 변조 최후)
  - Step 4b `prompt-change`·eval 확장: eval dataset 10분류(adversarial·
    historical-incidents·prompt-injection 등), historical-incidents 재투입
    루프(진화 루프와 접합)
  - 보관: Redis dataset 분류 스키마(cache/lock·rebuildable·fail-open/closed)
    — Redis 실사용 시점에
  - ~~공유 안전 정책 신설~~ → DR-016으로 해소 (Step 3에서 safety-policy.md 신설)
- 실행형 EXPLAIN 검토: db-reviewer는 현재 "전/후 EXPLAIN 첨부 요구"까지만
  (비실행). Step 5에서 SELECT-only 검증 스크립트 + read-only DB 계정 +
  tools allowlist 3층으로 도입 검토 — EXPLAIN ANALYZE는 쿼리를 실제
  실행하므로(DML 부작용) 구조적 차단이 전제 (2026-08-22 결정).

- ~~`entity-design`을 독립 스킬로 유지할지~~ → DR-018로 해소 (유지 확정,
  1단/2단 산출물 구조 확정, With/Without 생략 — 트리거 실측은
  baseline-summary, Codex의 DR-011 ① 쟁점은 1단/2단 분리로 해소).
- (예약) entity-design 워크플로우 확장 후보 — erd-design 레퍼런스의 미도입
  절차 (DR-018 등재). ~~DBML 논리 모델링·물리 모델링 단계~~ → DR-019로
  스킬에 반영. 남은 것:
  - docs/conventions에 용어 사전(도메인 용어 SSOT) 신설 — conventions
    추가라 사람 승인 필요
- `ship-pr`을 스킬로 둘지 공유 reference 문서로 둘지: 단독 호출 가치
  기준으로 Step 4에서 확정 (DR-008).
  (`issue-context`는 "MOI-xxx 분석해줘"로 단독 호출됨 → 스킬로 확정, Step 2)
- ~~Codex용 커스텀 에이전트 정의(toml) 필요성~~ → DR-013으로 해소
  (프롬프트 주입 방식, 어댑터 미도입. 마찰 실측 시 재검토).
- DR-004·008은 승인(잠정): Step 6에서 트리거 양성/음성/경계 프롬프트로
  레포 기준 측정 후 확정.
- Step 5 게이트 범위 확장 (토스 스킬 품질 루브릭 참고,
  toss.tech/article/skill-quality-rubric, 2026-08-21 검토):
  - 스킬 구조 lint — frontmatter 파싱·name kebab/폴더 일치·description
    길이·본문 줄수 상한·references 중첩 금지. 로컬 훅과 CI가 같은 스크립트
    공유. 근거: "호출 안 되는 원인이 frontmatter 한 줄"이라는 토스 실측 +
    우리 확률/결정론 분리 원칙 (트리거 eval 앞단의 싼 게이트)
  - 결함 기반 판정 — 절대 합격선 대신 BLOCKER(머지 차단)/MAJOR(수정 요구)
    2단 분류. 축2 "합격선 미정" 공백의 해법
  - 안전성 — 스킬·하네스 파일의 평문 시크릿·파괴적 명령 패턴 검출
    (False Negative 최소화 우선)
  - 미도입: 30항목 루브릭 전체·LLM 판정 자동화·S~F 등급 (2인 팀 규모 아님)
- AGENTS.md 라우팅·포인터의 구체 문안: Step 6에서 확정.
  - 행동 원칙 4줄(변경은 단순하게 · 필요한 부분만 · 근본 원인 · 무관 코드
    수정 금지)을 포인터에 포함할지 — 멘토팀 관행이나, 일반 지침의 효과는
    실험상 미미(arXiv "Evaluating AGENTS.md")해 Step 6에서 판단.
- 이슈 스펙 최소 기준(목적·무엇·관련·컨텍스트)을 팀 가이드로 문서화할지:
  이슈 품질이 하네스 성패를 좌우한다는 멘토링 관찰. Linear 템플릿 운영과
  겹치므로 Step 6에서 위치 결정.
- 스킬·에이전트 신설 시 메타 리뷰(스킬 리뷰어류) 공식화 여부: 현재는
  사람 검토 + Codex 교차 검토로 수행 중. 신설 빈도가 낮아지는 완공 후
  가치를 재평가 (멘토팀은 공식 문서 기반 리뷰어 스킬 운용).
- worklog GC의 구체 주기·조건: 운영 경험이 쌓인 뒤 결정 (DR-006).
