# entity-design 트리거 세트

핵심 경계: 1단(초안)과 2단(PR) 모두 이 스킬. n3(단독 인덱스 추가)은
구현 워크플로우 내 소규모 스키마 변경 소관이라 음성. b2(컬럼 2개+
엔티티)는 entity-design 2단 vs req-impl의 경계.

러너 입력은 [entity-design.tsv](entity-design.tsv).

| id | 유형 | 프롬프트 | 기대 |
| --- | --- | --- | --- |
| p1 | 양성 | 새 PRD 나왔어. MOI-721 엔티티 모델링부터 하자 | yes |
| p2 | 양성 | MOI-722 리뷰 도메인 테이블 설계해줘 | yes |
| p3 | 양성 | MOI-723 ERD 초안 잡아서 회의 준비해줘 | yes |
| p4 | 양성 | 결제 기능 들어온대. MOI-724 엔티티 관계랑 상태 필드 초안 그려줘 | yes |
| p5 | 양성 | MOI-725 합의된 설계 schema.sql이랑 마이그레이션까지 반영해줘 | yes |
| n1 | 음성 | MOI-726 구현해줘 | no |
| n2 | 음성 | 이 쿼리 왜 느린지 봐줘 | no |
| n3 | 음성 | room 테이블에 인덱스 하나만 추가해줘 | no |
| n4 | 음성 | participation 도메인 구조가 어떻게 돼있는지 설명해줘 | no |
| n5 | 음성 | seed.sql 참조 데이터 갱신해줘 | no |
| b1 | 경계 | MOI-727 이 기능은 테이블부터 API까지 다 새로 필요해 | boundary |
| b2 | 경계 | MOI-728 room 테이블에 컬럼 두 개 추가하고 엔티티도 맞춰줘 | boundary |
