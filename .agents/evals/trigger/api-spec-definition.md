# api-spec-definition 트리거 세트

핵심 경계: Service를 만들지 않는다. n1(구현)·n2(연결)·n3(엔티티)가
3자 near-miss다. b2(응답 필드 추가)는 스펙 변경 vs 구현의 경계.

러너 입력은 [api-spec-definition.tsv](api-spec-definition.tsv).

| id | 유형 | 프롬프트 | 기대 |
| --- | --- | --- | --- |
| p1 | 양성 | 프론트가 급하대. MOI-701 API 스펙만 먼저 정의해줘 | yes |
| p2 | 양성 | 방 목록 조회 API 계약 만들어줘, 모킹까지. 이슈 MOI-702 | yes |
| p3 | 양성 | MOI-703 와이어프레임 나왔어. Controller랑 DTO 스펙 잡아줘 | yes |
| p4 | 양성 | MOI-704 RestDocs 문서까지 포함해서 API 정의해줘 | yes |
| p5 | 양성 | 프론트 병렬 작업용으로 MOI-705 목업 API 먼저 배포하자 | yes |
| n1 | 음성 | MOI-706 구현해줘 | no |
| n2 | 음성 | MOI-707 만들어둔 서비스를 API에 연결해줘 | no |
| n3 | 음성 | 새 PRD 나왔어. 엔티티 모델링부터 하자 | no |
| n4 | 음성 | openapi3.yaml 생성이 왜 실패하는지 봐줘 | no |
| n5 | 음성 | participation API 문서 어디서 보는지 알려줘 | no |
| b1 | 경계 | MOI-708 API부터 만들고 구현까지 쭉 가자 | boundary |
| b2 | 경계 | MOI-709 이 API 응답에 필드 하나 추가해줘 | boundary |
