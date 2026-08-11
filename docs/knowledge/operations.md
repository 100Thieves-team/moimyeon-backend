# 운영 지식

배포·헬스 체크·외부 인프라 연결에서 실제로 겪은 장애를 기록한다.

## 우리가 겪은 것

- 2026-08-11: 알림 Redis 자원 없이 `redis-core`를 조립한 뒤 ECS 배포가 반복해서 롤백됐다.
  원인: ALB가 모든 HealthContributor를 합산하는 `/actuator/health`를 사용해 Redis 연결 실패를
  API 트래픽 수용 불가로 판단했다. 재발 방지: core-api readiness 그룹에 DB만 포함하고,
  Terraform 헬스 체크 경로를 `/actuator/health/readiness`로 고정한다.
