# PR99 후속 수정 맥락

사용자는 PR98 머지, PR87 최신 dev·스펙 확인 후 보완·머지, PR99의 컴파일·마이그레이션·배포 연결 문제 수정·검증 순서 진행을 승인했다(2026-09-18).

PR98과 PR87은 dev에 머지 완료했다. 이번 PR99의 비교 기준 dev는 85d886ca3d93bbebe121f111b200f0f3ff10114b이다.

- 이슈: MOI-471. 진행 시작 8시간이 지나면 클로징 미제출 룸도 완료한다.
- 자동 종료를 실제 배포하는 core-worker에 배치한다. 신규 인프라나 운영 리소스 변경은 없다.
- 전이 전 동일 룸 행 잠금·상태 재확인, 룸별 트랜잭션, SYSTEM 로그 및 실패 격리를 유지한다.
- V27은 아직 dev에 없는 미머지 마이그레이션이다. 새 컬럼을 처음부터 NOT NULL DEFAULT MEMBER로 추가해 기존 행 및 구버전 API INSERT를 보존한다.
- API/Flyway 성공 후 worker 배포 순서를 사용한다. dev worker 수는 1, live는 0이므로 live worker 활성화는 별도 운영 작업이다.
- 원래 PR99는 최신 dev에서 테스트 생성자 호출 하나가 컴파일되지 않았으며 이를 byMember 팩토리로 수정했다.

실행 검증: 최종 코드 트리에서 ktlintCheck, 전체 test 1,136개, core-api restDocsTest 234개, core-worker bootJar 모두 통과. MySQL 8.4.9 Testcontainers에서 V26→V27 업그레이드·기존 행 보존·구버전 INSERT·SYSTEM 쓰기 검증 통과. 실제 WorkerApplication 실행, 8시간 경계, 동시 호출 로그 1개, 재실행·부분 실패 테스트 포함. 배포 workflow 계약, 설정 profile gate 및 worker JAR의 작업 클래스 포함 확인.

독립 QA·DB 최종 판정은 PASS이며 필수 수정 사항은 없다. 동시 종료 검증 DB는 H2이고, MySQL 검증 범위는 마이그레이션 호환성이다. 운영 DDL 소요시간·잠금 대기는 미측정이며 NULL 허용 변경 시 테이블 재구축 가능성이 있다. 구버전 애플리케이션 쓰기는 호환되지만 SYSTEM NULL 행 생성 후 스키마를 되돌리려면 별도 데이터 처리가 필요하다.
