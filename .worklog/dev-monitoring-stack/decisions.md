# 구현 결정

## 수집 경로와 범위

API와 Worker의 Micrometer 메트릭을 OTLP/HTTP로 private Collector에 전송한다.
Prometheus가 이를 스크레이프하고 Grafana가 조회한다. Sentry는 오류 이벤트에 한정해
연동하며 traces/profiles는 끈다. 일반 로그 전체를 Sentry나 신규 S3로 복제하지 않는다.
기존 awslogs, ALB와 WAF 로그 경로는 유지한다.

## dev 전용 단일 호스트

모니터링은 private t3.small, 암호화 gp3 20GiB에 구성한다. 이미지와 Compose의
버전·체크섬을 고정한다. dev AMI도 실제 서울 리전의 available/x86_64 이미지를 확인해 고정했다.
새 S3에는 비민감 설정 배포 파일만 저장한다. 메트릭과 Grafana DB는 EBS에 저장한다.
Grafana와 Prometheus는 loopback 바인딩, 관리자 접근은 SSM 터널로 제한한다.
한 노드·한 AZ이므로 HA나 백업 완료를 주장하지 않는다.

## 계측 신뢰성과 개인정보

서비스/인스턴스/환경/배포 SHA를 구분한다. HTTP histogram 단위는 초로 통일한다.
앱 heartbeat와 Collector up을 별도로 본다. OTLP 전송이 끊기면 오래된 샘플은
90초 만료 및 다음 스크레이프를 거쳐 사라진다. 데이터 부재를 무조건 0으로 대체하지 않는다.
Worker ACK 대기는 그룹 단위 중복 Gauge이므로 합산하지 않고 max를 사용한다.
Sentry는 예외 메시지·사용자·요청·MDC 대신 예외 종류와 스택 코드 위치만 허용한다.
Throwable 없는 ERROR의 분류 개선은 안전한 오류 코드 설계가 필요한 후속 작업이다.

## 부팅 실패와 복구

QA에서 EBS/SSM 준비 unit의 dependency 실패는 상위 unit의 Restart로 복구되지 않는
문제를 확인했다. 초기 dev에서는 자동 무한 재시도 대신 fail-closed와 명시적 운영자 복구를
채택했다. 자동 재시도 주장 제거, 실패 메시지, 세 unit 확인과 reset-failed/start 절차를
런북에 추가했다. Terraform 성공과 EC2 running만으로 배포 완료를 선언하지 않는다.
실제 EC2 재부팅·EBS 데이터 보존·준비 실패 복구 검증은 배포 후 수용 조건이다.

## 배포 권한 경계

비밀값은 사전 생성한 SSM SecureString ARN으로만 참조한다. 로컬에서는 값과
raw plan/state를 읽지 않고 apply도 실행하지 않는다. PR 초안 승인 후 CI의 sanitized plan을
검토한다. 사람이 dev에 머지하면 CI apply가 자동 실행되므로 머지 승인은 apply 승인이다.

## checkout pin CI 회귀

고정된 checkout 개수 조건은 job 추가를 보안 정책 위반으로 오인했다. 숫자만 3으로
바꾸지 않고 YAML의 실제 jobs/steps/uses를 읽어 모든 checkout을 기존 승인 SHA와
비교한다. 태그·브랜치·다른 SHA·무버전 사용 및 checkout 부재는 실패시킨다.
합성 양성/음성 사례와 실제 CI 파일을 gate self-test에서 함께 검사한다.
PR 전 로컬 검증에는 개별 게이트뿐 아니라 `bash .agents/gates/tests/run.sh`도 포함한다.
