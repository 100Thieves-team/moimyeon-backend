# dev 모니터링 구현 계획

기준: 2026-09-11에 fetch한 `origin/dev` (`8f873640`). 이슈 없는 사용자 요청.

- [x] 컨텍스트 수집 및 구현: Grafana, Prometheus, Sentry 중심의 승인된 구성도 반영
- [x] 정적 검증, 계측 회귀 테스트, 로컬 수집 경로 검증
- [x] 리뷰 및 PR 초안 승인
- [ ] PR CI의 sanitized Terraform plan 판독 및 사람 승인
- [ ] 사람이 dev 머지 후 CI apply/deploy 결과 및 수집 상태 확인

체크박스는 사람 승인 기록이며 실행 완료만으로 체크하지 않는다.

2026-09-11 사용자 "생성해줘"로 제출한 구현·검증 결과와 PR 초안 승인.
이 승인은 커밋·push·draft PR 생성까지이며 CI plan 승인이나 머지 승인이 아니다.

## 완료 조건

- API·Worker의 OTLP 메트릭이 private Collector를 거쳐 Prometheus에서 서로 구분된다.
- Grafana는 SSM 터널과 로그인을 통해 접근하며 데이터는 암호화된 별도 gp3 EBS에 보존한다.
- Sentry는 명시적으로 활성화하고 API·Worker DSN은 사전 생성 SSM ARN으로만 참조한다.
- HTTP/JVM/DB pool/기존 Worker 지표와 마지막 메트릭 수신 시각을 확인한다.
- 단일 dev 노드이며 HA, Tempo, Loki, FireLens, exporter 추가 및 비용 실험은 제외한다.
- 기존 awslogs와 ALB 로그를 삭제하거나 재배선하지 않는다. live는 비활성 기본값을 유지한다.

## 배포 경계

`infra-change`와 `docs/knowledge/infra.md`에 따라 로컬 apply와 AWS 콘솔 변경은 금지한다.
PR 초안 승인 전 push하지 않고, raw plan/state는 읽지 않는다.
dev 머지는 CI apply와 앱 배포를 유발하므로 plan 검토 후 사람의 승인이 필요하다.

## 현재 전제 조건

- GitHub 및 AWS dev 계정 접근 확인(읽기 전용), 원본 dirty worktree 보존.
- Docker Desktop을 시작해 고유한 테스트 프로젝트로 Collector/Prometheus/Grafana 수집 검증 완료.
- SSM 이름만 조회한 결과 Sentry DSN 두 개 및 Grafana 관리자 비밀번호가 아직 없다.
- 프론트엔드 저장소가 지정되지 않아 해당 SDK 연동은 별도 확인이 필요하다.

## 실행 기록

- `./gradlew test ktlintCheck` 전체 통과(JDK 25), API·Worker bootJar 통과.
- Terraform dev/live/shared validate 및 fmt 통과. 로컬 plan/apply는 실행하지 않음.
- 합성 OTLP 2개 인스턴스 수집, Grafana 인증 검사와 계측 회귀 테스트 통과.
- QA 필수 지적에 따라 infra-change 구현 단계에서 첫 부팅 dependency 실패의
  자동 재시도 주장을 제거하고 실패 탐지·운영자 재시도 절차를 추가함. 읽기 전용 재리뷰 PASS.
- 확장 smoke에서 초 단위 histogram, 전체 대시보드 PromQL, Collector가 정상인 상태의
  앱 heartbeat 만료까지 통과. 테스트 컨테이너는 종료했고 기존 컨테이너는 변경하지 않음.
- PR 초안 승인 후 ship-pr 절차로 커밋·push·draft PR 생성 진행. CI plan 승인은 별도 대기.
