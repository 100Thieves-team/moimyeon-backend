# MOI-549 컨텍스트

## 요구사항

[MOI-549: 하네스, Agents.md 개선](https://linear.app/100-thieves/issue/MOI-549)은 팀원이 변경 이유와 동작을 이해하기 쉽게 만들고, 구현과 기획의 어긋남을 줄이는 작업이다. 사용자가 수정 계획을 승인하고 구현 진행을 요청했다.

- 배경·이유, 비즈니스 시퀀스, 필요시 시스템 아키텍처, 변경 결과를 Claude Artifact로 설명하고 PR에 링크한다.
- 코드 구현 전에 LLM Wiki의 모호점을 해소하고 필요한 내용을 갱신한다. 구현 후에는 Wiki와 코드의 정합성을 확인한다. Notion은 SSOT가 아니다.
- 관련 제품 요구사항·스펙 또는 필요한 ADR을 참조·수정한 경우 해당 Wiki 주소를 PR에 넣는다. 하네스·저장소 내부 문서 변경에는 Wiki를 요구하지 않는다.
- PR 변경 파일 수는 20개 이하를 권장하며 50개를 초과하지 않는다. 큰 작업은 스택 PR로 나눈다.
- 리뷰 수정은 별도 커밋을 계속 추가하지 않고 기존 커밋에 반영한 뒤 force push한다. 보호 브랜치 및 `--force-with-lease` 제한은 유지한다.

이슈 본문을 확인했고 댓글·연결 문서는 없었다. 제품 기능 PRD가 필요한 작업은 아니므로 하네스와 문서 운영 자료를 근거로 삼았다.

사용자 확인: 설명 자료는 **Claude Artifact를 우선하되 다른 공유 가능한 문서도 허용**한다. 이 답변을 이슈의 매체 지정에 우선 적용한다.

계획 논의 반영: Wiki 공통 조건은 6개 변경 스킬에 복제하지 않고 공통 진입·종료 지점에 둔다. `ship-pr`은 확인 결과와 필요한 갱신의 완료 여부를 점검하는 마지막 게이트로 한정한다. 사용자는 **필요한 명세 갱신을 절대로 후속 TODO로 넘기지 말 것**을 명시했다. 갱신은 같은 작업에서 끝내야 하며, 미완료 상태를 별도 이슈나 사람의 나중 작업으로 돌려 현재 작업을 완료 처리할 수 없다.

최종 승인: 사용자가 “나머지 계획은 다 승인할게”라고 확인했다. 위 수정사항을 포함한 전체 계획을 승인된 기준으로 삼는다. 하네스 구현·변경 후 검증의 완료 여부와는 구분한다.

## Wiki 근거

- [Plady 기술 문서 운영 방식](wiki://100thieves/topics/t-plady-기술-문서-운영-방식): ADR·Tech Spec·Review Summary의 역할과 문서 과잉 방지 원칙. 사용자의 최종 범위 설명에 따라 모든 PR에 Wiki 링크를 강제하지 않는다. 하네스 규칙은 저장소에서 관리한다.
- [moimyeon 상태 SSOT 코드 동기화 점검](wiki://100thieves/topics/t-moimyeon-ssot-code-sync-audit): 불일치를 정책 변경·문서 보강·구현 공백으로 분류하고, 상태 SSOT와 PRD의 책임을 분리한 선례.
- `wiki_rules(section="PRD")`: 2026-08-24 이후 Notion은 현행 원본이 아니다. `raw/product/`의 PRD 변경은 `policy/_src/`의 관련 기록·기준 문서 날짜 동기화까지 같은 작업에서 처리한다. 렌더링된 policy 페이지와 조립된 상태-SSOT.yaml은 직접 수정하지 않는다.

Wiki의 공통 도구 설명에는 raw 생성 전용 제약이 있고 PRD 세부 규칙에는 원본 갱신 절차가 있다. 구현 단계에서는 실제 변경 대상과 허용된 MCP 쓰기 방식을 확인해야 한다. 로컬 사본 편집으로 제약을 우회하지 않는다.

## 현재 하네스의 관련 지점

| 위치 | 확인한 상태 |
| --- | --- |
| `AGENTS.md` | 맥락이 부족할 때만 Wiki를 조회. 구현 전후 필수 게이트는 없음 |
| `.agents/README.md` | `.agents/` 단일 소스, 각 워크플로우가 오케스트레이션, 별도 전역 오케스트레이터 없음 |
| `.agents/execution-policy.md` | 정책 원본과 스킬 단계 인라인의 동시 수정 필요. PR 생성 후 worklog 수정 제한 |
| `.agents/skills/issue-context/SKILL.md` | 컨텍스트 수집 전용. Notion PRD 검색을 필수 순서로 남겨 둠 |
| `.agents/skills/ship-pr/SKILL.md` | PRD 수정은 사람에게 후속으로 넘김. Wiki·설명 자료·파일 수 확인 없음. 분기된 계보는 일괄 중단 |
| `docs/conventions/git.md` | 리뷰 반영을 `fix(review)` 등의 별도 커밋으로 명시. 스택 PR과 lease push는 이미 존재 |
| `.github/PULL_REQUEST_TEMPLATE.md` | Wiki와 설명 자료 링크·정합성 결과·PR 범위 필드 없음 |
| `.agents/agents/qa-reviewer.md` | 비즈니스 의미와 diff를 대조하지만 Wiki 최신성·정합성 입력 계약은 없음 |
| `.agents/gates/lint_skills.py` | 스킬 구조와 일부 정책만 검사. MAJOR는 경고이며 차단 아님 |
| `.github/workflows/ci.yml`, `review-swarm.yml` | PR base가 main/dev일 때만 실행. 중간 브랜치를 base로 한 스택 PR에는 실행되지 않음 |
| `.github/workflows/deploy-aws.yml` | dev에서 성공한 push CI만 배포 진입. PR CI 대상 확대 시 이 경계 보존 필요 |
| `.claude/skills` | 다른 개발자의 절대경로를 가리키는 추적 symlink. 이식성과 새 스킬 발견에 영향 |
| `.agents/evals/run.sh` | 결과 경로가 MOI-474에 고정. 새 작업 평가에 그대로 사용하면 산출물 위치 계약 위반 |

## 보존할 것

- `.agents/`와 런타임 symlink 구조, 기존 워크플로우 경계, 읽기 전용 리뷰어.
- `.worklog/{작업키}/`의 계획·결정 기록. `_workspace/`는 개인 스크래치이며 새 팀 문서 저장소로 바꾸지 않는다.
- 코드가 현재 동작의 근거라는 원칙. 불일치는 드러내고 분류하며, 코드에 맞춰 기획을 자동 승인하지 않는다.
- 시크릿 보호, 운영 변경 금지, 보호 브랜치 제한, 기존 체크포인트와 재시도 상한.

## 계획 수립 중 확인한 검증

- `python3 .agents/gates/lint_skills.py`: 통과.
- `bash .agents/gates/tests/run.sh`: 통과. 내부 checkout pin 회귀 테스트 13개 포함.
- 이는 변경 전 기준선이다. 제안한 새 동작의 검증 결과가 아니다.

## 구현 착수 후 사용자 범위 명확화

제품 요구사항·스펙 또는 필요한 ADR이 관련된 경우에만 Wiki를 참조한다. 하네스는 코드 저장소 안에서 충분히 관리하므로 Wiki 조회·수정·링크를 요구하지 않는다. 이를 무조건 필수로 해석해 Wiki에 추가했던 하네스 규칙은 원복하고 재조회로 확인했다. 필요한 명세 갱신을 같은 작업에서 끝내는 조건은 적용 대상 작업에서 유지한다.
