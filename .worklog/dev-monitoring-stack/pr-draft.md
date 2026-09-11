# feat(infra): dev 전용 Grafana Prometheus Sentry 모니터링 추가

2026-09-11 사용자가 승인한 draft PR 본문이다. AWS 배포는 수행하지 않았다.

## 개요

fetch한 최신 `origin/dev`의 `8f873640`을 기준으로 API·Worker 모니터링 경로와
dev 전용 수집 인프라를 추가한다. 브랜치는 `codex/dev-monitoring-stack`이다.

## 변경 사항

- API·Worker OTLP 계측, heartbeat, HTTP histogram, 서비스/인스턴스/환경/배포 SHA 구분.
- Sentry Boot4 연동, 오류 이벤트 개인정보 allowlist, 중복 예외 억제, 기본 비활성 정책.
- private EC2의 Collector·Prometheus·Grafana, 암호화 EBS와 private DNS, SSM 관리자 접근.
- 14개 Grafana 패널, 이미지/Compose 체크섬 고정, 설정 배포 전용 private S3.
- dev Terraform 배선, 최소 IAM/SG, 신규 세 비밀값의 plan 역할 읽기 거부.
- 실제 컨테이너 수집 smoke와 계약 테스트를 CI에 연결, 배포 SHA 환경변수 전달.

## 맥락·결정

- 기존 ECS awslogs, ALB→S3, WAF 로그는 변경하지 않는다. 신규 로그 S3 적재는 없다.
- Tempo, Loki, FireLens, 추가 exporter, 비용 최적화 실험은 제외한다.
- dev 단일 노드다. 모니터링 호스트 교체 시 관측 공백은 발생할 수 있다.
- EBS 보존은 백업/HA와 다르다. `prevent_destroy`는 임의 삭제·해체를 막는다.
- storage 준비 실패는 자동 복구를 보장하지 않으며, 명시적 운영자 복구와 readiness 확인을 따른다.
- Sentry 수집에서 원문 메시지를 제거한다. 원문 진단은 기존 로그와 함께 수행한다.

## 후속 작업·TODO

- 운영자가 서울 리전에 SSM SecureString 세 개를 사전 생성하고 이름/타입만 확인한다.
  - `/moimyeon/dev/core-api/SENTRY_DSN`
  - `/moimyeon/dev/core-worker/SENTRY_DSN`
  - `/moimyeon/dev/monitoring/GRAFANA_ADMIN_PASSWORD`
- 프론트엔드 저장소 미지정: 프론트 Sentry SDK는 이번 백엔드 변경에 포함되지 않는다.
- 개인정보 없는 오류 코드/fingerprint 설계로 Throwable 없는 ERROR 분류를 개선한다.
- 배포 후 실제 API·Worker 메트릭, Sentry 수신, EC2 재부팅·EBS 데이터 보존과 실패 복구를 검증한다.
- 실제 부하 기준 용량/보존량·OOM·CPU 크레딧을 관찰한다. 최적화 효과는 아직 측정하지 않았다.

## 검증

- PASS: JDK25 `./gradlew test ktlintCheck`, API·Worker bootJar.
- PASS: OTLP protobuf·histogram/리소스 속성, Sentry 개인정보 제거·중복 예외 회귀 테스트.
- PASS: dev/live/shared Terraform validate, fmt, 기존 배포·설정·CI 계약과 신규 인프라 계약.
- PASS: 6개 모니터링 설정 계약 테스트, 변경 파일 비밀값 검사, `git diff --check`.
- PASS: 실제 Docker Collector/Prometheus/Grafana 설정·health·익명 접근 거부.
- PASS: 두 인스턴스 OTLP 수집, HTTP histogram 초 단위, 전체 대시보드 PromQL 문법.
- PASS: Collector up=1인 상태에서도 전송을 멈춘 앱 heartbeat 만료.
- 미실행: AWS CI plan/apply, 실제 dev 신규 인프라 검증, Sentry SaaS 수신.
- QA PASS: 초기 부팅 실패 지적을 수동 복구 설계로 반영하고 재리뷰 통과.
  새 SSM 셸의 Compose 명령에 필요한 파일 경로 환경변수도 런북에 반영했다.

## 배포 노트

**Draft 상태에서 CI plan 요약을 보완**하며, plan과 SSM 준비 확인 전 머지하지 않는다.
그 전에는 AWS 변경 수를 추정값으로 채우지 않는다.

| 환경 | 소스상 영향 | CI plan 추가/변경/파괴 수 |
| --- | --- | --- |
| dev | 모니터링 자원 신설, API·Worker task 설정 및 SG/SSM 참조 추가 | PR CI 대기 |
| shared | 공개 AMI/비민감 설정 읽기 및 신규 비밀값 읽기 거부 | PR CI 대기 |
| live | Terraform 모니터링 자원 비활성. 공통 라이브러리 변경은 이후 live 승격 시 적용 | PR CI 대기 |

CI plan에서 unexpected replacement/삭제, 기존 데이터 저장소 변경, public ingress가
없는지 검토한다. 새 HTTPS egress `0.0.0.0/0`은 기존 NAT를 통해 SSM/S3/패키지/레지스트리에
접근하기 위한 것이며, 관리자 포트 ingress는 공개하지 않는다.

**dev 머지 승인 = CI apply 승인**이다. 사람의 plan 승인과 세 SSM 준비 확인 전에는 머지하지 않는다.
로컬/에이전트 apply나 우회 배포는 수행하지 않는다.
배포는 기존 CI → shared plan/apply → dev plan/apply → 변수 동기화 → API rollout → Worker rollout 경로를 따른다.
live의 정적 validate와 달리 실제 PR plan 실행 여부는 기존 live CI 활성화 설정에 따른다.
런북: `infra/observability/README.md`.

## 커밋 단위

1. `feat(observability): 서비스 OTLP 계측과 Sentry 오류 수집 추가`
2. `feat(infra): dev 전용 모니터링 스택과 검증 경로 추가`

중간 커밋의 빌드·테스트도 확인한 뒤 PR을 생성한다. 이슈 없는 요청이므로 가상의 Closes 키는 넣지 않는다.
