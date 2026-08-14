# 운영 지식

배포·헬스 체크·외부 인프라 연결에서 실제로 겪은 장애를 기록한다.

## 우리가 겪은 것

- 2026-08-14: dev API가 운영 프론트 오리진과 apex 쿠키 도메인을 그대로 사용해 preview 프론트
  도입 시 운영·개발 세션이 충돌할 수 있었다. 재발 방지: dev OAuth·CORS·쿠키 범위를
  `dev.moimyeon.plady.io`로 묶고, S3 CORS와 알림 클릭 URL도 같은 프론트로 Terraform 검증한다.
- 2026-08-12: core-api 새 리비전이 `PROVISIONING`에 머물러 배포 작업이 제한 시간을 넘겼다.
  원인: API, Worker, Redis가 `t3.small` 3대를 사용한 상태에서 API 롤링 교체용 네 번째 인스턴스가
  필요했지만 ASG `max_size`가 3이었다. 재발 방지: dev ASG 최대 크기를 4로 두고 정적 검사로
  하한을 고정한다. 실제 대수 축소는 ECS Capacity Provider가 계속 담당한다.
- 2026-08-12: Terraform apply가 ECS ASG를 1대로 줄인 직후 Capacity Provider가 3대로 늘려
  `want exactly 1 ... have 3` 상태로 끝나지 않았다. 원인: Terraform의 `desired_capacity`와
  ECS managed scaling이 같은 값을 함께 관리했다. 재발 방지: Terraform은 ASG `min_size`와
  `max_size`만 관리하고 실제 대수는 Capacity Provider가 단독으로 결정한다. 기본 목표 사용률은
  100%로 두고 ECS 서비스는 CPU 기준 `binpack`으로 빈 용량을 먼저 사용한다.
- 2026-08-12: core-worker가 MySQL 8.4 인증 중 연결 제한 시간을 넘겨 반복 종료됐다.
  원인: 0.25 vCPU Worker가 `db-core.yml`의 1.1초 연결 제한과 dev Flyway 활성화까지 함께 상속했다.
  재발 방지: Worker 전용 설정을 마지막에 import해 Flyway를 끄고 연결 대기를 10초로 분리하며,
  Worker 기본 자원을 0.5 vCPU와 768MB로 올린다.
- 2026-08-12: 알림 기능 배포 뒤 core-api가 Outbox 스키마 검증에서 종료됐다.
  원인: Hibernate 7이 `@Lob String`을 MySQL `LONGTEXT`로 해석했지만 Flyway는 `TEXT`를 만들었고,
  H2 테스트가 이 방언별 타입 차이를 드러내지 못했다. 재발 방지: `TEXT` 매핑은
  `SqlTypes.LONGVARCHAR`로 명시하고, MySQL 8.4 Testcontainer에서 전체 Flyway 적용 후 JPA 검증을 실행한다.
- 2026-08-11: 알림 Redis 자원 없이 `redis-core`를 조립한 뒤 ECS 배포가 반복해서 롤백됐다.
  원인: ALB가 모든 HealthContributor를 합산하는 `/actuator/health`를 사용해 Redis 연결 실패를
  API 트래픽 수용 불가로 판단했다. 재발 방지: core-api readiness 그룹에 DB만 포함하고,
  Terraform 헬스 체크 경로를 `/actuator/health/readiness`로 고정한다.
- 2026-08-11: ECS 회로 차단기가 이전 Task Definition으로 롤백한 뒤에도 배포 작업이 제한 시간까지
  기다렸고 Target Health 진단도 권한 오류로 중단됐다. 재발 방지: 요청한 Deployment가 사라지고
  이전 PRIMARY가 복구된 상태를 즉시 실패로 판정하며, 배포 역할에 Target Health 조회 권한을 둔다.
