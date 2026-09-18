# requirement-implementation 트리거 세트

기대값 의미: 해당 프롬프트에서 `requirement-implementation` 스킬이
선택돼야 하면 `yes`, 다른 스킬·직접 응답이 맞으면 `no`(괄호에 올바른 대상),
판단 여지가 있으면 `boundary`.

러너 입력은 [requirement-implementation.tsv](requirement-implementation.tsv).

| id | 유형 | 프롬프트 | 기대 |
| --- | --- | --- | --- |
| p1 | 양성 | MOI-501 구현해줘 | yes |
| p2 | 양성 | 이 이슈 요구사항 구현하자: MOI-502 | yes |
| p3 | 양성 | 모임 참여 취소 기능 만들어줘. 이슈는 MOI-503 | yes |
| p4 | 양성 | MOI-504 티켓 처리해줘. PRD 보고 서비스 로직까지 | yes |
| p5 | 양성 | 요구사항대로 기능 붙여줘, 이슈 MOI-505 | yes |
| n1 | 음성 | 프론트가 급하대. MOI-506 API 스펙만 먼저 정의해줘 | no (api-spec-definition) |
| n2 | 음성 | MOI-507: 만들어둔 서비스를 API에 연결해줘 | no (api-connection) |
| n3 | 음성 | 새 PRD 나왔어. 엔티티 모델링부터 하자 | no (entity-design) |
| n4 | 음성 | RoomService 변경분 코드 리뷰해줘 | no (리뷰만) |
| n5 | 음성 | participation 도메인 구조가 어떻게 돼있는지 설명해줘 | no (질문) |
| b1 | 경계 | MOI-508 구현해줘. 화면이랑 요구사항 다 나와있어 | boundary (스펙→연결→구현 연쇄의 시작점 판단) |
| b2 | 경계 | MOI-509 서비스 테스트부터 짜줘 | boundary (req-impl 단계 4 진입) |

near-miss(n1~n3)가 핵심이다 — "명백히 무관한 프롬프트"는 측정 가치가 없다.
