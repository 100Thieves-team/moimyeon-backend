이슈 키 없는 작업. 사용자 요청과 최신 dev 코드를 기준으로 수집했다.

# 요구사항

기존에 그린 dev Grafana·Prometheus·Sentry 구성도를 구현하고 배포 가능한 상태로 만든다.
메트릭은 API·Worker → OTel Collector → Prometheus → Grafana로 흐른다.
별도 private EC2에 Docker Compose, 암호화된 gp3 EBS, SSM 접근을 사용한다.
Sentry SaaS는 오류와 원인 문맥 수집부터 시작하며 tracing은 이번 범위에서 비활성화한다.

# 관련 코드

- `support/monitoring`: Actuator, registry와 exporter 설정. 현재 공개 포트는 health만 노출.
- `support/logging`: Sentry Logback appender와 프로파일별 로깅 설정.
- `storage/redis-core`: 처리 결과 Counter 및 ACK 대기 Gauge가 이미 존재한다.
- `core/core-api`, `core/core-worker`: 독립 실행·배포 단위.
- `infra/terraform/modules/moimyeon-environment`: API·Worker ECS, 네트워크, IAM.
- `infra/terraform/envs/dev/dev.tfvars`: 비민감 dev 설정 원본.
- `.github/workflows/terraform-plan*.yml`, `terraform-apply*.yml`: PR plan과 머지 후 적용 경계.
- `.github/workflows/deploy-aws.yml`: 성공한 dev CI 및 Terraform 적용에 종속된 배포.

# 범위 및 확인된 사실

원본 디렉터리와 이전 `codex/dev-observability` 작업의 변경은 수정하지 않는다.
새 worktree는 `.worktrees/dev-monitoring`, 브랜치는 `codex/dev-monitoring-stack`이다.
AWS account/region은 기존 dev Terraform을 따른다. live 모니터링 자원은 비활성이다.
shared CI IAM에는 공개 AMI/비민감 설정 읽기와 신규 앱 비밀값의 명시적 읽기 거부만 추가한다.
SSM 값은 조회하지 않았고 필요한 파라미터 이름이 없다는 사실만 확인했다.
실제 신규 리소스 배포 또는 Sentry 수집은 아직 수행하지 않았다.

# PR CI 회귀 수정

PR #123의 `harness-gates / Gate self-test`에서 `gitleaks-checkout-pin` 실패를 확인했다.
기존 검사는 CI checkout 개수를 2로 고정했으나 monitoring-smoke 추가로 3개가 되었다.
실제 모든 checkout은 승인된 SHA를 사용한다. 사용자가 개수 대신 각 checkout의 SHA를
검증하는 수정과 PR 반영을 승인했다. 수정 범위는 gate와 회귀 테스트이며 런타임/
Terraform 자원/배포 workflow는 변경하지 않는다. PR 생성 자동화가 연결한 이슈는 MOI-516이다.
