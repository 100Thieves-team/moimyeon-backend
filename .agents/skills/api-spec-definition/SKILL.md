---
name: api-spec-definition
description: 와이어프레임·PRD를 바탕으로 API 계약(Controller·Request/Response DTO·RestDocs 문서·모킹)을 정의한다. "API 스펙 먼저", "API 계약 만들어줘", "목업 API 배포", 프론트 병렬 작업용 API 선행 정의 요청 시 사용한다. Service·비즈니스 로직은 만들지 않는다 — 요구사항 구현은 requirement-implementation, 스펙과 Service의 연결은 api-connection.
---

# api-spec-definition — API 계약 정의

[execution-policy.md](../../execution-policy.md)·[security-policy.md](../../security-policy.md)를
따른다. `worklog/{이슈키}/`가 있으면 재실행 분기부터.

전제: Linear 이슈 키 + 화면·요구사항 근거(와이어프레임, PRD, 또는 사용자
제공 캡처). 근거가 없으면 스펙을 추측하지 말고 사람에게 요청한다.

## 단계

1. **worktree 준비** — `docs/conventions/git.md`.
2. **컨텍스트 수집** — `.agents/skills/issue-context/SKILL.md` 수행.
   와이어프레임·화면 정의를 반드시 확보한다.
3. **스펙 초안** — `worklog/{이슈키}/plan.md`에 작성:
   - URI: `docs/conventions/api-design.md`의 URI 절 전체를 읽고 적용
     (포함 관계 vs 필터 판정, ID 1개 제한, 제공 정보 표현, 행위 동사 템플릿)
   - 엔드포인트별 요청/응답 필드와 타입, 에러 케이스와 에러 코드
   **[체크포인트 A: 스펙 승인]**
4. **구현** — Controller + Request/Response DTO (api-design.md의 DTO·검증
   규칙). 모킹 컨트롤러는 api-design.md 모킹 패턴(`@Profile`, 정적 목업 값).
5. **RestDocs 테스트** — 성공 + 에러 케이스를 에러 코드와 함께 문서화
   (`docs/conventions/api-docs.md`). openapi3.yaml 생성을 확인한다.
6. **리뷰** — `.agents/agents/code-reviewer.md` 위임 (변경 파일 + context.md
   경로). "필수" 반영 상한 2회.
7. **검증** — `./gradlew test ktlintCheck` 통과.
   **[체크포인트 B: 구현 승인]**
8. **커밋·PR** — git.md 준수. 스펙 결정·미결정은 decisions.md·tbd.md에.

## 하지 않는 것

- Service·Implement·도메인 로직 생성 — 스펙과 모킹까지가 이 워크플로우다.
- 근거 없는 필드 추측 — 화면에 없는 필드는 tbd.md에 적고 묻는다.
