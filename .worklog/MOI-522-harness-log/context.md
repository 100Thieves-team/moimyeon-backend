# MOI-522 컨텍스트

[Linear 이슈](https://linear.app/100-thieves/issue/MOI-522/하네스-log-기록-개선)는
하네스가 worklog·docs에 커밋 해시를 기록하지 않게 하고, PR 생성 뒤 작업
기록만을 위해 추적 문서를 다시 수정하는 흐름을 없애는 작업이다. 댓글과 연결
문서는 없다.

## 요구사항

- 커밋 해시와 PR·CI의 현재 상태를 worklog·docs에 복제하지 않는다.
- PR 생성 뒤에는 리뷰 지적을 반영하는 코드·테스트·관련 문서 변경 외에 로컬
  작업 트리를 수정하지 않는다.
- `ship-pr` 재실행 안전성은 추적 문서의 복제 값 없이 유지한다.

## 관련 코드

- `.agents/skills/ship-pr/SKILL.md`: PR 생성 뒤 검증 SHA를 plan.md에 쓰고,
  다음 실행에서 그 값을 HEAD와 비교하는 현재 흐름.
- `.worklog/README.md`: 모든 plan.md에 검증 SHA를 요구하는 파일 계약.
- `.agents/execution-policy.md`, `AGENTS.md`: 하네스 공통 정책의 원본과 런타임
  진입점.
- `.agents/agents/*-reviewer.md`: PR 이후 리뷰 입력을 저장소 밖 임시 patch로
  받는 역할 계약.
- `.worklog/MOI-474/decisions.md` DR-033: 검증 SHA 계약을 도입한 과거 결정.

## 경계

- 과거 worklog의 커밋 해시는 당시 작업 이력으로 유지한다.
- Git 이력, PR 링크, CI 자체 기록은 변경하지 않는다.
- 애플리케이션 코드와 배포·운영 리소스는 변경하지 않는다.
