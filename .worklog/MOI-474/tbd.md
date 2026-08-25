# MOI-474 — 미결정 사항

- 전문 에이전트 블루프린트 소재 매핑 (로컬
  `_workspace/harness-reference/specialist-agents-blueprint.md`, 원전 링크
  29개 포함 — DR-015 소싱 소스. 해당 스텝 작성 시 벤더링):
  - Step 3 `db-reviewer`: SQL 리뷰 10단계 절차 + 검사항목, DDL 분석 출력
    스키마(INSTANT/INPLACE/COPY·lock·rollback — "ALTER니까 online 추정 금지",
    MySQL 원전), expand-contract 8단계 상세
  - ~~Step 4 `qa-reviewer`~~ → DR-022로 벤더링 완료
    (docs/knowledge/qa-review.md + release-checklist.md 시드)
  - ~~Step 4b `incident-response`·`prompt-change`~~ → DR-023으로 벤더링
    완료 (llm.md·data.md·infra.md 시드, 완화 사다리·10분류·재투입 루프)
  - 보관: Redis dataset 분류 스키마(cache/lock·rebuildable·fail-open/closed)
    — Redis 실사용 시점에
  - ~~공유 안전 정책 신설~~ → DR-016으로 해소 (Step 3에서 safety-policy.md 신설)
- ~~실행형 EXPLAIN 검토~~ → DR-025에서 운영 개시 후로 이연 확정
  (read-only DB 계정 인프라 전제. 원 결정: SELECT-only 스크립트 +
  read-only 계정 + tools allowlist 3층, 2026-08-22).

- ~~`entity-design`을 독립 스킬로 유지할지~~ → DR-018로 해소 (유지 확정,
  1단/2단 산출물 구조 확정, With/Without 생략 — 트리거 실측은
  baseline-summary, Codex의 DR-011 ① 쟁점은 1단/2단 분리로 해소).
- (예약) entity-design 워크플로우 확장 후보 — erd-design 레퍼런스의 미도입
  절차 (DR-018 등재). ~~DBML 논리 모델링·물리 모델링 단계~~ → DR-019로
  스킬에 반영. 남은 것:
  - ~~용어 사전(도메인 용어 SSOT)~~ → 2026-08-25 신설
    (docs/conventions/glossary.md, entity-design 1단이 참조)
- ~~`ship-pr`을 스킬로 둘지 공유 reference 문서로 둘지~~ → DR-021로 해소
  (스킬 확정 — DR-011 3중 테스트 통과, 워크플로우 4종 공통부 수렴).
- (예약) 팀 개발 플랫폼에 qa-engineer가 실제 배치되면 레포 qa-reviewer와의
  중복을 재평가한다 (DR-022 경계: 레포=머지 전 정적, 플랫폼=배포 후 런타임).
- (예약) 플랫폼 소관으로 이연한 것 (DR-023 배치 기준): 장애 탐지·자동
  완화 실행, AWS 레벨 운영 액션. infra-reviewer는 미도입 — infra-change
  사용 빈도 관찰 후 재평가.
- (예약) `docs/knowledge/infra.md`는 배포 개편 하위 이슈들이 상세를
  확정할 때마다 동기화한다 (Terraform CI 이전 완료 시 과도기 규칙 제거).
- ~~Codex용 커스텀 에이전트 정의(toml) 필요성~~ → DR-013으로 해소
  (프롬프트 주입 방식, 어댑터 미도입. 마찰 실측 시 재검토).
- ~~DR-004·008 잠정~~ → DR-029로 확정 (2026-08-25 실측: 양성 실질 18/18,
  오호출 0/9, sonnet·opus 집계 동일로 모델 교란 배제).
- Step 5 게이트 범위 확장 (토스 스킬 품질 루브릭 참고,
  toss.tech/article/skill-quality-rubric, 2026-08-21 검토):
  - 스킬 구조 lint — frontmatter 파싱·name kebab/폴더 일치·description
    길이·본문 줄수 상한·references 중첩 금지. 로컬 훅과 CI가 같은 스크립트
    공유. 근거: "호출 안 되는 원인이 frontmatter 한 줄"이라는 토스 실측 +
    우리 확률/결정론 분리 원칙 (트리거 eval 앞단의 싼 게이트)
  - 필수 정책 라인 lint — 워크플로우 스킬에 체크포인트·재시도 상한 인라인
    존재, 외부 콘텐츠를 읽는 스킬에 데이터 규칙 존재 검사. 원본은
    execution/safety-policy.md (DR-020 — 공유 참조 제거의 드리프트 대책)
  - 권한 통제 이관 — 파괴 명령·운영 경계를 settings.json deny 규칙·
    PreToolUse 훅 + Codex sandbox/approval로 집행 (DR-020 결정론 층)
  - 결함 기반 판정 — 절대 합격선 대신 BLOCKER(머지 차단)/MAJOR(수정 요구)
    2단 분류. 축2 "합격선 미정" 공백의 해법
  - 안전성 — 스킬·하네스 파일의 평문 시크릿·파괴적 명령 패턴 검출
    (False Negative 최소화 우선)
  - 미도입: 30항목 루브릭 전체·LLM 판정 자동화·S~F 등급 (2인 팀 규모 아님)
- ~~AGENTS.md 라우팅·포인터의 구체 문안~~ → DR-026으로 해소 (9행 표 등재,
  행동 원칙 4줄은 미포함 확정).
  - ~~정책 압축 포함~~ → DR-020으로 선반영 (AGENTS.md "특히 조심할 것"에
    4줄 추가 완료, 2026-08-24). Step 6에서는 라우팅 표만 남음.
  - 행동 원칙 4줄(변경은 단순하게 · 필요한 부분만 · 근본 원인 · 무관 코드
    수정 금지)을 포인터에 포함할지 — 멘토팀 관행이나, 일반 지침의 효과는
    실험상 미미(arXiv "Evaluating AGENTS.md")해 Step 6에서 판단.
- ~~이슈 스펙 최소 기준 문서화·PRD→이슈 경로~~ → 이 레포의 작업이 아니다
  (2026-08-24 사용자 결정). issue-context의 빈약 이슈 반려 규칙만 유지.
- 스킬·에이전트 신설 시 메타 리뷰(스킬 리뷰어류) 공식화 여부: 현재는
  사람 검토 + Codex 교차 검토로 수행 중. 신설 빈도가 낮아지는 완공 후
  가치를 재평가 (멘토팀은 공식 문서 기반 리뷰어 스킬 운용).
- worklog GC의 구체 주기·조건: 운영 경험이 쌓인 뒤 결정 (DR-006).

- (예약) 백업·복원 검증: backup 존재 확인이 아니라 실제 복원 검증
  (블루프린트 §8). 실데이터 축적·런칭 시점에 도입 검토 — SLO 예약과
  동일한 시점 트리거 방식.
- ~~worktree 공통 절차화~~ → DR-026으로 해소 (git.md에 worktree 절 추가).
- ~~라우팅 표 행 수·test_authoring 판별 기준~~ → DR-026으로 해소 (9행,
  판별 기준 열에 명시).
- ~~라우팅 등록 후 재측정 + With/Without~~ → 2026-08-25 실행 완료
  (DR-028·029, baseline-summary).

- (예약) Codex 프로젝트 훅 로드 방식 스모크: Codex 0.149에 훅 이벤트
  모델(hooks.json·trust 해시)은 실재 — 레포 단위로 gate_hook.py를 싣는
  방법 확인되면 L1을 Codex에도 배선 (DR-025, 현재는 L2/L3 커버).
- (실행 대기) GitHub branch protection(main·dev): 사람 승인은 받았으나
  에이전트 권한으로 레포 설정 변경이 차단됨 — 사람이 직접 실행한다.
  명령은 _workspace/branch-protection.md.

- (예약) 무인 자동화의 체크포인트 통과 설계: 스킬은 체크포인트에서 턴을
  끝내므로(DR-009) 사람 없는 파이프라인에서는 완결되지 않는다. 실제로
  하네스를 무인 자동화에 물릴 때 결정한다 — 저위험 작업만 무인 허용,
  또는 결정을 전부 기록하는 조건부 통과 모드 (DR-028 비용 항목).

- (후속) qa-reviewer 권고 미반영분 (DR-030): lint_skills가 SKILL.md 없는
  디렉토리를 BLOCKER로 처리하는 문제, python3 부재 환경의 pre-commit 안내,
  pre-push의 remote 객체 부재 시 오도 메시지, .review-swarm.yaml
  teamRuleFiles 20000자 상한 확인, gate_hook·pre-push 자체 테스트 부재,
  .agents/agents/*.md frontmatter lint 부재.

- (후속 이슈) 2-ID path 엔드포인트 정리: `rooms/{roomId}/comments/{commentId}`
  (RoomCommentController), QuestionPreparationController·RoomApplicationController
  등이 api-design.md의 "path ID 1개" 규칙 이전에 작성됐다. 문서에 예외를
  명시해 뒀고(신규부터 적용), 실제 리팩토링은 별도 이슈 — 라우팅·API 계약·
  클라이언트 영향이 걸려 스코프가 다르다 (2026-08-25 사용자 결정).

- (후속) CodeRabbit 이연분 (DR-030 보강 3): pre-commit의 staged YAML 검증
  (`git show :path` — 스테이지 후 워크트리 복원 시나리오), 리뷰어 5종
  출력 계약 통일(ship-pr 취합이 형식 제각각을 파싱), infra.md 스모크
  테스트 계약을 배포 워크플로에 연결(app_url 동기화 선행), 리뷰어 입력의
  시크릿 redaction 명문화.

- (후속) Multi-Agent Review 이연분 (DR-030 보강 5): ~~①knowledge 문서 출처
  검증 불가~~ → 보강 6으로 해소(공개 원전 우선 + 자립 선언)
  ②`check_pairings.py`의 확장자 화이트리스트 하드코딩
  ③`lint_skills.py`의 `EXTERNAL_CONTENT_SKILLS` 하드코딩을 SKILL.md 마커
  기반 자동 판별로 전환.
