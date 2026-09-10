# dev monitoring: Prometheus, Grafana, Sentry

이 디렉터리는 배포 소스다. 파일 존재나 로컬 테스트 통과는 AWS 배포 완료를 뜻하지 않는다.

## 범위

- API·Worker: Micrometer OTLP/HTTP → private Collector → Prometheus → Grafana.
- 별도 private `t3.small` EC2, root 12GiB + 암호화된 gp3 데이터 EBS 20GiB.
- Prometheus 보존은 7일 또는 8GB 중 먼저 도달하는 한도다.
- Grafana와 Prometheus는 호스트 loopback에만 바인딩하며 SSM 터널로 접근한다.
- Sentry SaaS: 기본 비활성화, dev Terraform이 각각의 SSM DSN 참조와 활성화를 전달한다.
- 기존 ECS `awslogs`, WAF 로그, ALB→S3 경로를 변경하지 않는다.
- Tempo, Loki, FireLens, EC2/Redis exporters, 부하·비용 최적화 실험은 이번 범위가 아니다.
- 프론트엔드 SDK 변경은 이 저장소에 포함되지 않는다. 별도 저장소 확인이 필요하다.

## Sentry 수집 정책

ERROR 로그와 SDK 예외를 오류 이벤트로 처리하고 중복 예외 수집을 방지한다.
예외 종류·스택의 코드 위치·환경·서비스·배포 SHA를 보존한다. 예외 메시지, 요청,
헤더, 본문, 사용자 정보, 임의 MDC는 외부로 보내지 않는다. 이 초기 보수적 정책은
개인정보 유출 방지를 우선하므로 원문 진단은 기존 운영 로그를 함께 사용한다.
Breadcrumbs는 애플리케이션 로거·시각·레벨만 보존하며 메시지는 제거한다.

`SENTRY_LOGS_ENABLED=false`가 기본이다. 켜더라도 지금 허용하는 코드는 `service.ready`뿐이다.
일반 stdout 전체를 수집한다고 가정하지 않는다. 새 로그 이벤트는 안전한 필드·코드를 정의하고
privacy filter와 테스트를 확장한 뒤 도입한다. tracing/profiling은 이번 배포에서 끈다.
Worker retry/DLQ는 기존 메트릭으로 관측한다. 모든 재시도가 Sentry 오류 이슈가 되지는 않는다.

## 배포 전 운영자 준비

다음 SecureString을 서울 리전에 사전 생성한다. 값은 소스, tfvars, PR, 채팅에 넣지 않는다.
Terraform은 값 대신 ARN만 참조한다.

| SSM 이름 | 용도 |
| --- | --- |
| `/moimyeon/dev/core-api/SENTRY_DSN` | Core API Sentry 프로젝트 |
| `/moimyeon/dev/core-worker/SENTRY_DSN` | Worker Sentry 프로젝트 |
| `/moimyeon/dev/monitoring/GRAFANA_ADMIN_PASSWORD` | 최소 12자 Grafana 관리자 초기 비밀번호 |

현재 IAM은 AWS 관리 `aws/ssm` 암호화 키를 전제로 한다. 고객 관리 KMS 키를 사용하면
정확한 키 ARN에 한정한 복호화 권한을 별도 검토해야 한다.
Grafana 비밀번호는 EC2의 `/run`에만 파일로 받아 UID472가 읽도록 전달하며 Terraform state와
컨테이너 환경변수에는 값이 들어가지 않는다. **기존 Grafana DB의 비밀번호는 SSM 값을 바꿔도
자동 변경되지 않는다.** 초기 생성 이후 회전은 Grafana의 승인된 관리자 절차와 함께 수행한다.

## 검증과 배포 절차

1. 로컬 정적 검증과 전체 `./gradlew test ktlintCheck`.
2. PR 초안에 사람 승인 후 PR 생성. CI는 container smoke와 Terraform plan을 실행한다.
3. sanitized plan에서 dev 신규 자원, API·Worker task revision, shared 읽기 IAM 변경을 검토한다.
4. 세 SSM 파라미터 준비, 비용·중단 시간·복구 절차와 plan을 사람이 승인한다.
5. 사람이 dev에 머지하면 기존 CI → shared apply → dev plan/apply → API → Worker 배포를 따른다.
6. SSM 연결 후 컨테이너 상태, 실제 서비스별 heartbeat, 요청 지표와 Sentry 이벤트를 확인한다.

로컬/에이전트 `terraform apply`, 콘솔 수동 변경, 배포 게이트 우회는 금지한다.
raw plan/state는 열람하지 않고 CI의 sanitized 자원 변경 요약만 판독한다.
shared IAM 변경은 public AMI 조회와 dev 설정 S3 prefix 읽기에 한정되며,
plan 역할은 세 새 비밀 파라미터의 값을 명시적으로 읽을 수 없다.

### 로컬 검증

```sh
python3 infra/observability/tests/test_contract.py  # PyYAML 6.0.3, jq 필요
bash infra/terraform/tests/monitoring-infra-contract.sh
bash infra/observability/tests/smoke.sh             # Docker + Compose, curl, jq 필요
```

smoke는 고유한 Compose 프로젝트와 임의 loopback 포트를 사용한다. 더미 자격 증명만 쓰고
수집기·Prometheus 설정, Grafana health/익명 접근 거부, 두 인스턴스의 합성 OTLP 메트릭,
초 단위 HTTP histogram, 실제 대시보드 PromQL과 전송 중단 시 heartbeat 만료를 검증한다.
Collector가 살아 있어도 앱의 오래된 지표는 사라져야 한다. 종료 시 자신의 테스트
컨테이너만 내리고 테스트 데이터 경로를 출력한다.
실제 Sentry SaaS 전송과 AWS EBS 재연결은 이 테스트가 증명하지 않는다.

### 배포 후 읽기 전용 확인

Terraform의 `monitoring_instance_id` 출력에 해당하는 인스턴스를 사용한다.
아래 명령은 운영자가 실행하며 실제 instance ID를 넣는다.

```sh
aws ssm start-session --region ap-northeast-2 --target <instance-id> \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["3000"],"localPortNumber":["3300"]}'
```

브라우저에서 `http://127.0.0.1:3300`을 열고 admin으로 로그인한다. SSM 권한이 없는 사용자에게
직접 접근시키기 위해 SG를 공개하지 않는다. Prometheus 확인은 별도 터널로 9090을 전달한다.
호스트 세션에서는 `systemctl status moimyeon-monitoring-storage.service docker.service moimyeon-monitoring.service`, `findmnt /var/lib/moimyeon-monitoring`,
아래와 같이 컨테이너 상태를 확인한다. 경로만 전달하며 비밀번호 파일은 출력하지 않는다.

```sh
sudo env GRAFANA_ADMIN_PASSWORD_FILE=/run/moimyeon-monitoring/grafana_admin_password \
  docker compose -f <release-directory>/compose.yaml ps
```

Grafana `Moimyeon dev · service monitoring`에서 다음을 확인한다.

- API와 Worker 각각의 `observability_heartbeat_seconds` 존재 및 최근성.
- 테스트 요청 후 HTTP 요청량·p95·5xx 지표, JVM·DB pool 지표.
- Worker `success/retry/dead_letter` 처리량, `max by (consumer_group)` ACK 대기량.
- `up`은 Collector 스크레이프 성공일 뿐 앱 상태가 아니다. 데이터 부재는 0이 아니다.

승인된 dev 테스트 오류를 한 번 발생시켜 Sentry에서 정확한 서비스·환경·릴리스 및 개인정보
제거를 확인한다. 테스트용 공개 오류 엔드포인트를 추가하지 않는다.

### 첫 부팅 준비 실패와 운영자 재시도

storage 준비는 EBS를 최대 120초 기다린 후 실패 시 종료한다. SSM 조회 실패도 같은 방식이다.
이때 Docker와 monitoring은 dependency 실패로 시작하지 않으며 **자동 재시도를 보장하지 않는다.**
상위 unit의 `Restart=on-failure`는 이미 실행된 컨테이너 시작 스크립트의 실패에만 적용된다.
Terraform apply 성공 또는 EC2 running 상태만으로 배포 완료로 판정하면 안 된다.

운영자는 세 unit의 status 및 storage journal을 확인한다. 비밀번호 파일을 출력하지 않는다.
EBS attachment, 정확한 SSM 이름/타입, IAM 또는 네트워크 원인을 먼저 해결한 후, 승인된
SSM 호스트 세션에서 다음과 같이 재시도한다. 이 명령은 에이전트의 CI 우회 배포 경로가 아니다.

```sh
sudo systemctl reset-failed moimyeon-monitoring-storage.service docker.service moimyeon-monitoring.service
sudo systemctl start moimyeon-monitoring.service
systemctl is-active moimyeon-monitoring-storage.service docker.service moimyeon-monitoring.service
findmnt /var/lib/moimyeon-monitoring
```

세 unit active, 정확한 EBS mount, 컨테이너 readiness와 새 API·Worker heartbeat까지 확인해야
복구 완료다. 첫 dev 배포 검증에는 EC2 재부팅·EBS 재연결 후 데이터 보존 확인을 포함한다.
준비 실패 후 수동 복구의 실제 EC2 검증은 로컬 Compose smoke로 대체하지 않는다.

## 복구·운영 제한

- 설정 또는 AMI 변경은 단일 EC2 교체를 일으킬 수 있어 모니터링 중단이 있다.
  이 시기 서비스 트래픽은 기존 ECS에서 계속 처리된다. 메트릭 전송 실패는 관측 공백이다.
- `prevent_destroy` EBS를 새 호스트에 재연결한다. 정확한 volume ID를 확인하고,
  완전히 빈 새 디스크일 때만 XFS로 포맷한다. 기존 서명·파티션이 있으면 중단한다.
- Docker 시작은 EBS mount 및 비밀번호 준비 이후다. root disk로 조용히 대체하지 않는다.
- EBS는 백업이 아니다. AZ 장애 자동 복구, HA, 스냅샷 복구 검증은 별도 후속 과제다.
- 설정 롤백은 Git의 정확한 revision을 다시 배포한다. S3의 이전 설정이 항상 남는다고
  가정하지 않는다. API·Worker 롤백은 기존 exact deployment bundle 절차를 따른다.
- monitoring flag를 끄거나 데이터 EBS를 삭제하는 변경은 `prevent_destroy`에서 막힌다.
  데이터 보존·백업 결정을 사람이 한 뒤 별도 해체 작업으로 수행한다.
- 초기 메모리 한도는 Grafana512MiB / Prometheus768MiB / Collector256MiB다.
  OS 여유는 약512MiB로 작다. 실제 시계열 수·OOM·CPU 크레딧을 보고 조정한다.
- 이미지, Compose 바이너리와 dev AMI는 버전/체크섬을 고정했다. OS 패키지 업데이트와
  컨테이너 업그레이드는 별도 PR에서 검증한다. S3 체크섬은 전송 무결성 검증이지 서명 검증은 아니다.
