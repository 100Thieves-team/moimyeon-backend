---
name: api-connection
description: 정의된 API 스펙(Controller·모킹)과 구현된 Service를 연결한다. "서비스를 API에 연결해줘", "모킹 걷어내고 실구현으로", "배선해줘" 요청 시 사용한다. 스펙과 Service가 이미 존재함을 전제로 하며 — 스펙이 없으면 api-spec-definition, Service가 없으면 requirement-implementation이 선행이다.
---

# api-connection — 스펙과 Service 배선

진행 규칙: `[체크포인트]`에서는 산출물을 제시하고 **턴을 끝낸다** — 사람
승인 없이 다음 단계로 가지 않는다(사용자가 "끝까지 진행해"라고 명시하면
생략). `worklog/{이슈키}/`가 있으면 재실행이다 — plan.md 체크박스(사람이
승인한 단계에만 `[x]`)로 마지막 승인 지점을 찾아 그 다음부터 재개하고,
기존 산출물은 차분만 수정한다.

## 단계

1. **전제 확인** — 대상 엔드포인트의 Controller(모킹)와 구현된 Service가
   모두 존재하는지 확인한다. 없으면 진행하지 말고 어느 선행 워크플로우가
   필요한지 알리고 정지한다.
2. **worktree 준비** — `docs/conventions/git.md`.
3. **연결 계획** — `worklog/{이슈키}/plan.md`: 어느 Service를 어느
   엔드포인트에 잇는지, Facade 도입 여부(여러 Service 결과를 조합할 때만 —
   `docs/conventions/layers.md`), 계약 변경 유무(원칙: URI·응답 계약 불변).
   **[체크포인트 A: 계획 승인]**
4. **배선** — 모킹 스텁 제거, `toXxx()`/`from()` 변환 연결(api-design.md
   변환 방향), RestDocs 테스트를 실구현 기준으로 교체(모킹→실구현 전환 규칙).
5. **리뷰** — `.agents/agents/code-reviewer.md` 위임. 반영 상한 2회 —
   소진하면 plan.md에 사유를 기록하고 턴을 끝낸다.
6. **검증** — `./gradlew test ktlintCheck` 통과(실패 수정 상한 3회, 소진 시
   기록 후 턴 종료) + **도커로 기동해 curl로
   대표 시나리오(성공 1·에러 1 이상) 실호출**하고 요청·응답을
   `worklog/{이슈키}/`에 기록한다.
   (도구는 curl — 에이전트가 설치·GUI 의존 없이 실행·기록·판정할 수 있다.
   사람 탐색용은 Swagger UI가 이미 있다.)
   **[체크포인트 B: 구현 승인]**
7. **커밋·PR** — git.md 준수.

## 하지 않는 것

- Service·비즈니스 로직 수정 — 배선 중 Service에 빠진 것이 발견되면
  tbd.md에 기록하고 사람에게 알린다 (requirement-implementation 소관).
- URI·응답 계약 변경 — 필요하면 스펙 변경으로 되돌아간다 (체크포인트 A에서
  사람 승인).
