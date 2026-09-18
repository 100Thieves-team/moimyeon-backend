# api-connection 트리거 세트

핵심 경계: 스펙과 Service의 존재를 전제. n3(커넥션 풀)은 '연결'
동음이의 near-miss. b2는 배선 중 Service 부족 발견 — 스킬 규칙상
정지·에스컬레이트가 정답.

러너 입력은 [api-connection.tsv](api-connection.tsv).

| id | 유형 | 프롬프트 | 기대 |
| --- | --- | --- | --- |
| p1 | 양성 | MOI-711 만들어둔 RoomService를 API에 연결해줘 | yes |
| p2 | 양성 | MOI-712 모킹 걷어내고 실구현으로 연결하자 | yes |
| p3 | 양성 | 스펙이랑 서비스 다 있어. MOI-713 배선만 해줘 | yes |
| p4 | 양성 | MOI-714 Facade 필요한지 보고 컨트롤러랑 서비스 이어줘 | yes |
| p5 | 양성 | MOI-715 연결하고 도커로 실호출 확인까지 해줘 | yes |
| n1 | 음성 | MOI-716 API 스펙만 먼저 정의해줘 | no |
| n2 | 음성 | MOI-717 요구사항 구현해줘 | no |
| n3 | 음성 | HikariCP 커넥션 풀 설정 바꿔줘 | no |
| n4 | 음성 | 프론트가 CORS 에러 난대, 원인 봐줘 | no |
| n5 | 음성 | RoomController 변경분 코드 리뷰해줘 | no |
| b1 | 경계 | MOI-718 화면 다 나왔어. 스펙부터 연결까지 한 번에 가자 | boundary |
| b2 | 경계 | MOI-719 연결하는데 서비스에 메서드 하나 없더라, 추가하면서 이어줘 | boundary |
