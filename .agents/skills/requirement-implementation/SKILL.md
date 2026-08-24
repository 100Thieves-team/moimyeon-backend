---
name: requirement-implementation
description: Linear 이슈의 요구사항을 service 테스트로 스펙화하고 TDD로 구현한다. "MOI-xxx 구현해줘", 요구사항 구현, 서비스 로직·기능 구현 요청 시 사용한다. API 스펙만 정의하는 작업(Controller·DTO·RestDocs), 이미 구현된 Service를 API에 연결하는 작업, 엔티티·테이블 설계에는 쓰지 않는다.
---

# requirement-implementation — 요구사항 구현 (service TDD)

진행 규칙: `[체크포인트]`에서는 산출물을 제시하고 **턴을 끝낸다** — 사람
승인 없이 다음 단계로 가지 않는다(사용자가 "끝까지 진행해"라고 명시하면
생략). `worklog/{이슈키}/`가 이미 있으면 재실행이다 — plan.md 체크박스
(사람이 승인한 단계에만 `[x]`)로 마지막 승인 지점을 찾아 그 다음부터
재개하고, 기존 산출물은 차분만 수정한다. 없으면 초기 실행이다 — worklog 디렉토리와 plan.md(단계 체크리스트)부터 만든다.

전제: Linear 이슈 키가 주어진다. API 스펙이나 엔티티 변경이 선행돼야 하는
이슈면 해당 산출물이 이미 있는지 확인하고, 없으면 진행하지 말고 사람에게
알린다 (선행 워크플로우: api-spec-definition, entity-design).
프롬프트·모델 설정만의 변경이면 이 워크플로우가 아니라 prompt-change다.

## 단계

1. **worktree 준비** — 최신 dev에서 분기. 브랜치 규칙은
   `docs/conventions/git.md`.
2. **컨텍스트 수집** — `.agents/skills/issue-context/SKILL.md` 전체를 읽고
   그대로 수행한다. 산출물: `worklog/{이슈키}/context.md`.
3. **구현 계획** — `worklog/{이슈키}/plan.md`에 작성한다: 접근 방식,
   변경 지점(파일·영역), 만들 테스트 목록, API 문서(RestDocs·OpenAPI) 영향,
   영향받는 외부 소비자(프론트 등), 영향 범위.
   **[체크포인트 A: 계획 승인]**
4. **service 테스트 스켈레톤** — 요구사항을 테스트 이름과 시나리오로
   옮긴다. 요령: [references/test-skeleton.md](references/test-skeleton.md).
   구현 코드는 아직 쓰지 않는다.
   **[체크포인트 B: 스펙 승인]**
5. **TDD 구현**
   - 시작 전 `docs/conventions/layers.md`, `testing.md`, `errors.md`를
     정독한다. 엔티티·스키마를 건드리게 되면 `storage.md`도.
   - 테스트를 하나씩 통과시키며 진행한다. 스타일은
     `docs/conventions/kotlin-style.md`.
     테스트 실패 수정은 3회 상한 — 소진하면 plan.md에
     `실패(재시도 3회 소진): 사유`를 기록하고 턴을 끝낸다.
   - 구현 중 내린 결정은 `decisions.md`에, 요구사항 모호점은 `tbd.md`에
     즉시 기록한다. 형식: [references/worklog-forms.md](references/worklog-forms.md).
6. **리뷰** — `.agents/agents/code-reviewer.md`의 역할 계약을 읽혀
   서브에이전트에 위임한다. 입력으로 변경 파일 목록과 context.md 경로를
   준다. 위임 전 diff를 `worklog/{이슈키}/review-diff.patch`로 저장해 함께 준다.
   변경에 스키마·쿼리가 포함되면 `.agents/agents/db-reviewer.md`를,
   배치·데이터 이동·백필이 포함되면 `.agents/agents/data-reviewer.md`를
   같은 입력으로 함께 위임한다. "필수" 지적은 반영한다 — 반영 루프 상한
   2회, 소진하면 plan.md에 사유를 기록하고 턴을 끝낸다. 조용히 계속하거나
   조용히 포기하지 않는다.
7. **검증** — `./gradlew test ktlintCheck` 통과 확인.
   **[체크포인트 C: 구현 승인]**
8. **커밋·PR** — `.agents/skills/ship-pr/SKILL.md`를 수행한다 (검증·QA
   게이트·커밋 분할·PR·리뷰봇 대응).

## 산출물

- 구현 + 테스트 (커밋·PR)
- `worklog/{이슈키}/`: plan.md(승인 이력), context.md, decisions.md, tbd.md
