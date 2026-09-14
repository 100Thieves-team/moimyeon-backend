# MOI-411 컨텍스트

2026-09-14 사용자 정책 확정: TBD 추천안을 채택했고 live 담당자는 팀원 3명 전원이다. 보존·일반 로그 유실·WARN 고정창·ERROR 반복 간격·일일 보고·slow 시작값은 `decisions.md` DR-22에 기록했다. 실제 채널·계정 연결은 아직 수행하지 않았다.

최신 조사일은 2026-09-14이며 아래의 9/9·9/11 내용은 조사 이력이다.

2026-09-09, 현재 브랜치 `dev`에서 설계 요청을 수행했다. 구현 브랜치는 생성하지 않았다.

- [MOI-411](https://linear.app/100-thieves/issue/MOI-411): 운영에 필요한 로그 관련 셋팅 추가하기. kotlin-logging 활용, S3 로그 저장. In Progress. 댓글·연결 문서 없음.
- 사용자가 상세 강의와 제약을 직접 제공했다. 앱 S3 SDK 호출·Kafka·ELK 지양. 이번 범위는 `moimyeon-backend` 설계이고 `swm-secure-logger`는 참고 코드다.
- 별도 연결 PRD는 없으며 찾았다고 추정하지 않았다. 사용자 제공 요구사항과 팀 위키의 9/8 회의를 근거로 진행했다.
- [9/8 회의](https://team-100-thieves.slack.com/docs/T0AUQ9XFYA0/F0C02SYQEER): FE 클릭/화면 이벤트와 서버 비즈니스 흐름 분담 검토. 모니터링 도구는 미확정.
- 관련 코드: `support/logging`, `core/core-api/.../ApiControllerAdvice.kt`, `AsyncConfig.kt`, `ResumeService.kt`, `JsoupOpenGraphClient.kt`, `infra/terraform/modules/moimyeon-environment/{ecs,worker_ecs,waf,s3,network,variables}.tf`, `infra/terraform/envs/dev/main.tf`.
- API/worker는 stdout→awslogs→CloudWatch. 기본 보존 30일은 WAF와 공유. dev API는 t3.small에 CPU 2048·메모리 1600MiB를 예약한다.
- 참고 구현 `swm-secure-logger`는 `S3LogArchiveService`에서 Scheduled SDK 업로드를 한다. 사용자 제약과 맞지 않아 그대로 이식하지 않는다.
- 산출물: `_workspace/MOI-411_LOGGING_DESIGN.md`의 미확정 설계 제안. 코드·인프라·외부 이슈 변경은 수행하지 않는다.

## 2026-09-11 갱신: PR #123(MOI-516) 반영

- 팀원이 dev 전용 모니터링 스택을 머지했다(`a5ced207` 앱 계측, `27fc4b2d` 인프라). 워크로그는 `.worklog/dev-monitoring-stack/`, 배포 소스는 `infra/observability/`.
- 메트릭: API·Worker → OTLP/HTTP(4318) → private Collector → Prometheus(7일/8GB) → Grafana. 별도 private EC2 t3.small + 암호화 gp3 20GiB. SSM 터널로만 접근. Collector 파이프라인은 metrics뿐이다.
- Sentry: logback Appender 제거, `SentryPrivacyFilter`가 허용 목록으로 이벤트 재구성(메시지·MDC·요청 제거, 예외 타입·코드 위치·태그만). `SENTRY_ENABLED`·`SENTRY_LOGS_ENABLED=false`를 Terraform이 주입, DSN은 SSM SecureString ARN 참조.
- OTel starter는 `support/monitoring`으로 이동. tracing sampling 0.0, trace·log export off. `OTEL_SERVICE_NAME`·`DEPLOYMENT_ENVIRONMENT`는 Terraform, `APP_RELEASE`는 deploy workflow가 주입.
- 그 PR은 Loki·Tempo·FireLens·awslogs 변경을 명시적으로 제외했다. MOI-411의 로그 경로와 충돌하지 않는다.
- `monitoring-config` S3 버킷이 추가됐으나 설정 전용이며 로그 저장을 배제한다.
- 갱신 반영 문서: 설계 제안서, `decisions.md`(DR-03·06·07·10·11), `tbd.md`, `_workspace/MOI-411_LOGGING_TWO_TRACK.md` 4절.
- 역할 구도: Sentry(오류 이벤트)·Grafana(메트릭)는 팀원이 끝냈고, MOI-411은 세 번째 경로인 로그다. 로그의 고유 책임은 개별 사건 기록과 S3 보관이며 그로스 트랙의 유일한 출처다. 세 경로 책임 분담 표는 두 트랙 문서 2.1a절.
- 2026-09-11 산출물 공유: 관측성 아키텍처 다이어그램 `docs/architecture/observability-architecture.drawio{,.png}` (미커밋). LLM Wiki에 raw `raw/technical/architecture/moimyeon-backend-observability-2026-09-11`, source `sources/s-moimyeon-backend-observability-architecture`, topic `topics/t-moimyeon-관측성-아키텍처` 게시 (wiki commit 1087115).

## 2026-09-14 갱신

- 사용자 요청: 8종 로그, 수준, 마스킹, 보존, 알림, 환경별 설정을 문제·대안·선택 이유 중심으로 보강. 잘못 보낸 DB 블로그 내용은 이번 작업에서 제외한다.
- 사용자 기준: TRACE/DEBUG 3일, WARN 1분 5회 경보와 일일 보고, ERROR/FATAL 즉시. 추가 답변으로 ERROR 첫 발생 즉시·반복 묶음을 확정했다.
- 코드 기준 `b3ae0040`. MOI-411 재조회 결과 본문·상태에 설계 범위를 바꾸는 추가 내용 없음. AWS 상태·Sentry 콘솔 설정은 조회하지 않았다.
- `SentryPrivacyFilter.event()`가 context를 버리므로 trace 연결은 sampling 설정과 별도로 보강해야 한다. `log()`의 trace 복사와 오류 event 전송을 혼동하지 않는다.
- ErrorType에 정상 업무 거절 WARN이 다수 있다. 알림 전 의미별 분류가 필요하다. local/local-dev show_sql은 Logback 밖에서 출력된다.
- 고정 Logback 설정·프로파일 검증, DEBUG의 CloudWatch 전용 3일, ops 전수 CloudWatch 7일·S3 90일 제안, Sentry ERROR·CloudWatch WARN·운영용 일일 보고 경로로 설계를 보강했다.
- 본 설계와 `decisions.md` DR-13~21이 현재 기준이다. 이전 두 트랙 문서는 이력이며 새 원장·보존 표현으로 해석하지 않는다. 외부 위키·다이어그램은 이번 요청에서 갱신하지 않았다.
