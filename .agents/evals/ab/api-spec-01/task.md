# AB 태스크 api-spec-01

외부 의존 없이 재현 가능하도록 요구사항을 인라인으로 준다. 워크트리에서 실행.

## 프롬프트 (With/Without 동일)

> 다음 기능의 API 계약을 정의해줘. 이슈 키는 MOI-EVAL-02로 간주해.
> 구현(Service)은 아직 하지 않아.
>
> "회원은 관심 있는 모임(room)을 즐겨찾기할 수 있고, 자신의 즐겨찾기
> 목록을 최신순으로 조회할 수 있다."

## 판정 기준

기계 판정:

- [ ] A1. Controller·Request/Response DTO가 생성되고 Service는 생성되지 않았다
- [ ] A2. 모킹 컨트롤러에 `@Profile("local", "local-dev", "dev")` 적용
- [ ] A3. RestDocs 테스트가 성공·에러 케이스를 포함하고 통과한다
- [ ] A4. (With만) worklog/MOI-EVAL-02/plan.md 생성 + 체크포인트 A 정지

사람 판정:

- [ ] H1. URI가 api-design.md 판단 기준을 따른다 (포함 관계 vs 필터,
      인증 주체 `/members/me/...`, 복수형·kebab-case)
- [ ] H2. 응답이 id 참조 원칙을 따른다 (카탈로그성 데이터는 id만)
- [ ] H3. 모킹 값이 정적이다 (동적 흉내 금지 — api-design.md)
