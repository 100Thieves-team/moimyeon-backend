# MOI-411 로깅 설계 결정 기록

2026-09-18: 구현 과정에서 별도 확인 없이 정했던 선택을 사용자에게 설명했고 승인을 받았다. 현재 승인 범위는 DR-26에 기록했다. 환경별 XML 유지 결정(DR-25)도 그대로 따른다.

2026-09-14 정책 확정: 사용자가 추천안을 채택했다. live 담당자는 팀원 3명 전원이다. 확정 범위와 남은 연결·검증 사항은 DR-22를 따른다.

2026-09-14: 현재 선택은 [보강 설계](design.md)와 아래 DR-13~21을 따른다. DR-01~12와 9/11 갱신은 판단 이력으로 남긴다. 정상 요청의 CloudWatch 선별 전송은 후속 최적화로 바뀌었고, DEBUG/TRACE는 S3에 보내지 않는다. ERROR 첫 발생 즉시·반복 묶음은 사용자가 확정했다.

작성일: 2026-09-09. 갱신: 2026-09-11, PR #123(dev Grafana·Prometheus·Sentry, `a5ced207`·`27fc4b2d`) 반영. 바뀐 결정은 **[9/11 갱신]**으로 표시했다.

이 문서는 [설계 제안](design.md)에서 무엇을 선택했고 왜 선택했는지를 기록한다. 구현 방법의 상세 설명은 설계 문서에 두고, 여기에는 판단 근거·검토한 대안·대가·재검토 조건을 남긴다.

## 기록의 상태

- **요구사항으로 확정**: 사용자가 지정한 kotlin-logging 사용, S3 저장, 앱의 직접 S3 호출·Kafka·ELK 지양.
- **설계 선택안**: 위 요구사항과 현재 코드에 근거해 제안한 선택. 팀의 최종 합의나 구현 완료를 뜻하지 않는다.
- **검증 전 가정**: 보존 기간·버퍼 크기·업로드 간격·자원 예산 등 측정과 운영 정책 확인이 필요한 값.

근거는 사용자 요구사항, 저장소에서 확인한 코드, 팀 회의, 공식 문서로 구분한다. 실제 AWS 배포 상태·비용·성능은 측정하지 않았다. [MOI-411](https://linear.app/100-thieves/issue/MOI-411)은 kotlin-logging과 S3 저장을 명시하며, 별도 연결 PRD나 댓글은 없었다.

## 결정 목록

| ID | 설계 선택안 | 가장 큰 이유 |
| --- | --- | --- |
| DR-01 | 앱은 stdout까지, S3 전송은 Fluent Bit | 업로드 실패·재시도를 JVM 밖에서 처리 |
| DR-02 | ECS FireLens sidecar를 우선 검토 | 기존 ECS 로그 수집 경로를 확장 |
| DR-03 | S3 전체 보관 + CloudWatch 선별 검색 | 저장량과 즉시 검색 비용을 별도로 조절 |
| DR-04 | body 로깅 대신 허용 필드 기반 요청 요약 | 개인정보 수집과 메모리·직렬화 비용을 함께 줄임 |
| DR-05 | 메시지·예외·MDC까지 출력 계약에 포함 | body를 빼도 기존 로그 호출에서 원문이 유출될 수 있음 |
| DR-06 | Sentry는 허용 목록 필터 경로만 유지 **[9/11 갱신]** | Appender 제거는 완료. 원문이 나가지 않는 경로만 남김 |
| DR-07 | support:logging formatter + support:monitoring OTel 활용 **[9/11 갱신]** | 중복 모듈·추적 체계 없이 현재 경계를 유지 |
| DR-08 | 요청 완료 로그와 예외 진단 로그의 책임 분리 | 인증 실패 누락·성공 오판·중복 스택 방지 |
| DR-09 | 운영 로그는 비차단·유한 버퍼, 유실 가능 | 로그 저장 장애가 서비스 요청을 멈추지 않게 함 |
| DR-10 | 로그 전용 버킷·최소 권한·별도 보존 | 업무 파일과 운영 로그의 접근·파기 정책 분리 |
| DR-11 | ECS 자원 변경은 실험·plan 후 결정 | dev API가 이미 호스트의 2vCPU를 예약 |
| DR-12 | 그로스는 확정 사건부터, SID/UTM은 후속 | 운영 로깅과 제품 분석의 요구사항·정확성 구분 |

## DR-01. 애플리케이션 출력과 S3 전송을 분리한다

**상태: 설계 선택안.** kotlin-logging과 S3 사용 자체는 사용자 요구사항이다.

**선택:** kotlin-logging → SLF4J/Logback → JSON stdout까지 애플리케이션이 맡는다. 압축·업로드·재시도는 Fluent Bit가 담당한다. S3에는 여러 이벤트를 묶은 새로운 객체를 생성한다.

**근거:** 사용자는 앱에서 직접 S3 라이브러리를 호출하는 구조를 지양했다. 참고 프로젝트의 `S3LogArchiveService`는 `@Scheduled`로 JVM 안에서 파일 탐색과 SDK 업로드를 수행하므로 이 제약에 맞지 않는다. 현재 [API ECS 설정](../../infra/terraform/modules/moimyeon-environment/ecs.tf)도 이미 stdout을 외부 로그 드라이버에 넘긴다.

**대안:** 커스텀 S3 Appender, 앱 업로드 스케줄러, 앱 파일을 host collector가 tail하는 방식. 앞의 두 방식은 업로드 생명주기를 앱이 책임지며, 파일 방식은 mount·rotation·offset 관리가 추가된다.

**대가:** 별도 수집기를 운영해야 하고, 로그 호출 성공이 S3 저장 완료를 의미하지 않는다. 앱 외부 버퍼와 전달 상태를 관측해야 한다.

**재검토:** 런타임이 ECS에서 바뀌거나 파일 전달이 더 자연스러운 환경이 되면 전송 경로만 교체한다. 앱의 출력 계약은 유지한다.

## DR-02. ECS의 FireLens sidecar를 우선 선택한다

**상태: 자원 검증을 전제로 한 설계 선택안.**

**선택:** API/worker task에 Fluent Bit sidecar를 추가하고 로그 드라이버를 `awsfirelens`로 바꾼다. 별도 검색 클러스터나 메시지 브로커는 추가하지 않는다.

**근거:** [API](../../infra/terraform/modules/moimyeon-environment/ecs.tf)와 [worker](../../infra/terraform/modules/moimyeon-environment/worker_ecs.tf)는 이미 ECS Service로 정의되어 있다. FireLens는 이 환경에서 로그 라우팅을 구성하는 기능이다. [AWS FireLens](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/using_firelens.html)

**대안:** 기존 awslogs → CloudWatch subscription → Data Firehose → S3. sidecar 자원 변경을 줄일 수 있지만 전체 CloudWatch ingestion과 Firehose 비용이 생긴다. 이 대안을 오버엔지니어링으로 일괄 배제하지는 않았다.

**대가:** task별 메모리·CPU, 이미지 업데이트, 재시작·종료 순서, 로컬 버퍼 관리가 필요하다. 현재 호스트에서 배치 가능하다는 검증은 끝나지 않았다.

**재검토:** sidecar 때문에 늘어나는 EC2 비용과 운영 부담이 관리형 전달 비용보다 크면 Firehose 경로를 선택한다. 현재 선택은 비용 최저가를 입증한 결과가 아니다.

## DR-03. S3 보관과 CloudWatch 즉시 검색을 나눈다

**상태: 설계 선택안. 검색 범위와 보존 기간은 정책 미확정.**

**선택:** 생성한 안전한 운영 로그는 S3에 보관한다. CloudWatch에는 실패 요청·WARN/ERROR·느린 요청·선정한 주요 사건을 보낸다. 오래된 상세 로그는 Athena로 필요한 때 조회한다.

**근거:** S3 저장은 요구사항이고, 현재 CloudWatch 검색 경로는 이미 있다. 모든 로그를 실시간 검색 저장소에 중복 적재할 필요가 있는지는 아직 확인되지 않았다. 저장량과 즉시 검색량을 독립적으로 조절할 수 있게 둔다.

**대안:** CloudWatch에도 요청 요약 전체를 전송. 초기 로그량이 적거나 정상 여정의 즉시 조회가 잦으면 더 적합할 수 있다. 매일 ExportTask로 S3에 내보내는 방식은 연속 아카이브 주 경로로 선택하지 않았다. [AWS의 연속 아카이브 안내](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/S3Export.html)

**대가:** 선별 저장한 CloudWatch만으로는 정상 요청의 전체 여정을 복원할 수 없다. S3 조회에는 업로드 대기와 Athena 실행 시간이 필요하다. gzip JSON은 초기 구현을 단순하게 하지만 잦은 분석의 최적 형식이라는 의미는 아니다.

**재검토:** 정상 요청 전체의 즉시 조회가 자주 필요하면 CloudWatch 전송 범위를 늘린다. Athena scan 비용과 분석 빈도가 커지면 저장 형식·집계 방식을 다시 검토한다. MAU만으로 검색 클러스터 도입을 결정하지 않는다.

**[9/11 갱신]** 요청량·p95·5xx 비율·JVM·DB pool·Worker 처리량은 Prometheus/Grafana 메트릭(7일 보존)이 담당한다. CloudWatch 선별 로그는 개별 실패 요청의 traceId·route·errorCode 조회용이며 비율 대시보드를 로그로 중복 구축하지 않는다. `slow` 임계값은 `http.server.requests` SLO 버킷과 맞춘다. Loki는 모니터링 PR이 명시적으로 제외했고, 도입하더라도 S3 보관 요구를 대체하지 않는다.

## DR-04. 요청 body를 수집하지 않고 허용 필드만 기록한다

**상태: 설계 선택안.**

**선택:** method·route template·status·duration·traceId·안전한 오류 코드 중심의 요청 요약을 남긴다. body, query, 쿠키, 헤더 덤프, 전체 URL, 이력서·후기·LLM 원문은 제외한다. health probe도 제외한다.

**근거:** 사용자 강의가 지적한 개인정보 평문 저장과 로그 비용을 동시에 줄이는 선택이다. body를 읽지 않으면 ContentCachingWrapper에 의한 복사·캐싱을 추가할 필요도 없다. 현재 서비스는 이력서·소개·후기처럼 자유 입력을 다룬다.

**대안:** body 전체 수집 후 정규식 마스킹. 임의 텍스트와 새로운 필드의 민감정보를 빠짐없이 인식한다는 가정이 필요하고, 마스킹 전 읽기·복사 비용도 남는다.

**대가:** 운영 로그만으로 원래 요청을 그대로 재현할 수 없다. 재현에는 오류 코드·배포 버전·테스트 입력·허용된 상태 정보를 활용해야 한다.

**재검토:** 특정 장애에 추가 필드가 필요하면 해당 사건의 필드·목적·보존 기간을 개별 검토한다. 전체 body 수집을 기본값으로 돌리지는 않는다. route 미매칭도 raw URI 대신 고정 분류값으로 기록한다.

## DR-05. 안전성의 경계를 최종 출력 이벤트 전체로 잡는다

**상태: 설계 선택안. formatter의 구체 구현은 미검증.**

**선택:** 고정 사건명과 사건별 허용 필드로 출력한다. message·argument·MDC·key-value·throwable cause/suppressed까지 정책을 적용한다. 예외 원문 메시지는 제외하고 오류 코드·예외 클래스·제한된 스택 프레임을 남긴다. formatter 실패 시에도 raw 원문으로 되돌아가지 않는다.

**근거:** [ApiControllerAdvice](../../core/core-api/src/main/kotlin/io/plady/moimyeon/core/api/controller/ApiControllerAdvice.kt)는 예외 메시지와 throwable을 출력한다. [ResumeService](../../core/core-api/src/main/kotlin/io/plady/moimyeon/core/domain/resume/ResumeService.kt)와 [JsoupOpenGraphClient](../../core/core-api/src/main/kotlin/io/plady/moimyeon/core/domain/jobposting/JsoupOpenGraphClient.kt)는 식별자·URL을 출력한다. body 로거만 제거해도 이 경로는 남는다.

**대안:** `%msg`에만 MaskingConverter 적용, JSON의 키만 허용 목록으로 제한. 둘 다 허용된 message 내부나 다른 이벤트 필드의 원문을 통제하지 못한다.

**대가:** 자유 형식 라이브러리 메시지를 보수적으로 제한하면 진단 정보가 줄고, 안전하게 허용할 메시지의 관리 비용이 생긴다. 정규식을 모든 문제의 해결책으로 삼는 대신 이 운영 비용을 수용하는 제안이다.

**재검토·검증:** 운영에 필요한 프레임워크 진단이 부족하면 안전한 메시지를 추가한다. raw canary를 각 입력 경로에 넣어 최종 출력 bytes를 검사한다. 클래스명이나 값의 길이만으로 개인정보 안전성을 판단하지 않는다. JSON 전체 크기는 유효한 구조를 유지하며 제한한다.

## DR-06. Sentry는 허용 목록 필터를 통과한 오류 이벤트만 내보낸다

**상태: [9/11 갱신] 코드 반영 완료(PR #123). dev 실제 수신 검증은 배포 후 수용 조건.**

**원래 선택(9/9):** dev/live의 Sentry Appender를 초기 로깅 구성에서 비활성화한다. 근거는 두 logback XML이 root logger에 STDOUT과 SENTRY를 각각 연결해 Console formatter의 정제를 우회한다는 점이었다.

**현재 상태:** 팀원의 PR #123이 두 XML에서 SENTRY Appender를 제거하고, `sentry-spring-boot-4-starter`와 [SentryPrivacyFilter](../../support/logging/src/main/kotlin/io/plady/moimyeon/support/logging/SentryPrivacyFilter.kt)로 대체했다. 필터는 beforeSend·beforeBreadcrumb·beforeSendLog에서 이벤트를 허용 목록으로 재구성한다. 예외 메시지·cause 메시지·MDC·요청·사용자 정보는 버리고 예외 타입·스택 코드 위치·service/environment/release 태그만 남긴다. Sentry Logs는 `service.ready` 한 코드만 통과한다. `send-default-pii: false`, tracing·profiling 0. dev에서는 `enable_monitoring`이 `SENTRY_ENABLED=true`, `SENTRY_LOGS_ENABLED=false`를 주입하고 DSN은 사전 생성 SSM SecureString ARN으로만 참조한다.

**갱신된 선택:** 이 경로를 그대로 둔다. 로깅 설계는 Sentry에 원문을 흘리는 별도 경로가 없다는 전제에서 stdout 정제에 집중한다. stdout formatter가 쓰는 사건명 허용 목록과 Sentry 필터의 `SAFE_LOG_EVENTS`가 별도 코드로 갈라지지 않도록, 사건명 레지스트리를 `support:logging`에서 한 번 정의하고 두 필터가 참조하는 안을 검토한다.

**대가:** Sentry 이슈에는 예외 메시지가 없으므로 원인 파악에 stdout/S3 로그의 errorCode·traceId가 필요하다. Throwable 없는 ERROR는 서로 구분되지 않는다는 QA 후속 권고가 남아 있다. 이 문서의 고정 사건명·오류 코드 설계가 그 권고의 해법이 될 수 있다.

**재검토·검증:** 배포 후 dev 테스트 오류 1건으로 서비스·환경·릴리스·개인정보 제거를 확인한다. raw canary를 `SentryPrivacyFilterTest`에도 넣는다. Sentry Logs 허용 코드를 늘리려면 필터와 테스트를 먼저 확장한다. ALB/WAF 로그가 앱 formatter 보호 범위 밖이라는 점은 그대로다.

## DR-07. 기존 로깅 모듈과 관측 체계를 확장한다

**상태: 설계 선택안. 기존 context 전파의 정상 동작은 미검증.**

**선택:** 공통 출력 정책은 `support:logging`, Servlet 처리는 `core-api/api`에 둔다. kotlin-logging은 직접 사용하는 모듈에 facade 의존으로 추가한다. traceId는 기존 OTel 기반을 활용한다. **[9/11 갱신]** 로그의 service·environment·release는 메트릭 resource attribute와 Sentry 태그가 읽는 `OTEL_SERVICE_NAME`·`DEPLOYMENT_ENVIRONMENT`·`APP_RELEASE`에서 같은 값을 읽는다.

**근거:** **[9/11 갱신]** OTel starter는 [support/monitoring/build.gradle.kts](../../support/monitoring/build.gradle.kts)에 있고(`support/logging`에는 Sentry 의존만 남았다) 실행 모듈들이 이미 support:logging과 support:monitoring을 함께 사용한다. [모듈 규칙](../../docs/conventions/modules.md)과 [인증 규칙](../../docs/conventions/auth.md)은 API와 Security의 접점을 표준 Principal로 제한한다.

**대안:** 새 범용 request-logger 모듈, 자체 traceId 필터, Sleuth 추가, 공통 logging 모듈에 Servlet/Security 의존 추가. 현재 구조에서 중복 책임과 의존을 늘린다.

**대가:** HTTP·비동기·worker의 context 연결은 각각 검증해야 한다. [AsyncConfig](../../core/core-api/src/main/kotlin/io/plady/moimyeon/core/api/config/AsyncConfig.kt)의 수동 executor에 OTel starter 의존만으로 전파가 보장된다고 볼 수 없다.

**재검토·검증:** 요청과 예외 로그의 trace 연결, `@Async`의 context 전파·복원, 다음 작업으로의 MDC 누출을 테스트한다. worker는 우선 eventId로 연결하고 자동 HTTP trace 연결을 약속하지 않는다. **[9/11 갱신]** `monitoring.yml`은 tracing sampling 0.0, trace·log OTLP export off다. 이 상태에서 MDC `traceId`가 채워지는지가 첫 검증이며, 채워지지 않으면 sampling·export 정책이 로깅의 선행 결정이 된다.

## DR-08. 요청 완료와 예외 진단의 기록 지점을 분리한다

**상태: 설계 선택안. 필터 순서와 완료 훅은 테스트로 확정해야 함.**

**선택:** Security 체인을 감싸고 observation scope 안에 있는 필터가 요청 완료 요약을 맡는다. MVC에서는 route template·표준 Principal을 수집한다. 스택은 예외를 최종 처리하는 경계 한 곳에서 기록한다.

**근거:** [인증 규칙](../../docs/conventions/auth.md)에 따르면 OAuth·401·403은 MVC 이전 Security 필터에서 처리될 수 있다. MVC interceptor만으로 기록하면 이 요청들을 놓친다. 한 예외를 계층마다 다시 기록하면 저장량과 장애 분석의 중복이 늘어난다.

**대안:** interceptor 단독 로깅, 모든 계층의 catch-and-log, 필터 `finally`에서 무조건 현재 status 출력.

**대가:** ERROR/async dispatch와 최종 응답 시점을 다뤄야 하므로 단순 전후 로그보다 구현이 까다롭다. HTTP 상태별 요청 통계와 기존 ErrorType의 진단 logLevel은 별도로 해석해야 한다.

**검증:** 401·403·404·500·OAuth·정상 요청에서 최종 status와 요청당 한 번 출력되는지 확인한다. 비동기 응답은 완료 훅을 검증한다. 경로 미매칭에서 raw URI로 fallback하지 않는다.

## DR-09. 일반 운영 로그는 서비스 가용성을 우선한다

**상태: 로그 유실 허용 범위에 대한 운영 합의가 필요한 설계 선택안.**

**선택:** JVM·로그 드라이버·수집기 버퍼를 유한하게 두고 비차단 전달을 지향한다. 포화·task 교체·호스트 장애에서 로그 손실을 허용하는 best-effort 경로로 정의한다. router는 `essential=false`와 재시작·누락 감시를 제안한다.

**근거:** S3가 느리거나 실패했을 때 요청 스레드를 기다리게 하면 저장 장애가 서비스 장애로 이어진다. 무한 큐는 메모리/디스크 고갈로 같은 문제를 늦춰 발생시킨다. 반대로 router가 essential이면 종료가 앱 task 종료로 전파될 수 있어 가용성 목표와 충돌한다.

**대안:** 큐 포화 시 차단, 무한 버퍼, 모든 사건의 durable queue/outbox 기록. 각각 요청 지연·자원 고갈·DB 쓰기 및 전달 관리 비용을 만든다. 무손실 감사 요구가 확정되면 마지막 대안은 별도 검토 대상이다.

**대가:** ERROR도 유실될 수 있으며 API 응답 성공과 로그 영속화를 동일하게 보장하지 않는다. router가 죽어도 앱이 계속 돌면 무로그 상태가 생길 수 있어 감시·복구 절차가 필수다.

**재검토·검증:** 감사·정산 용도나 허용 불가능한 손실 요구가 생기면 전송 계약을 다시 설계한다. S3 거부·router 종료·큐/디스크 포화·배포 종료·호스트 교체를 각각 시험한다. 일반 output 성공 카운터만으로 S3 저장 완료를 판단하지 않고 실제 canary 객체 도착을 확인한다. [Fluent Bit S3의 버퍼·모니터링 제약](https://docs.fluentbit.io/manual/data-pipeline/outputs/s3)

## DR-10. 업무 파일과 로그의 권한·보존을 분리한다

**상태: 설계 선택안. 보존 기간과 IAM 공유 허용은 확인 필요.**

**선택:** 로그 전용 S3 버킷을 두고 공개 차단·TLS·저장 암호화·최소 쓰기 권한을 적용한다. 운영자 조회 권한은 별도로 둔다. 앱 로그와 WAF·감사 목적 로그의 보존 정책을 분리한다.

**근거:** [s3.tf](../../infra/terraform/modules/moimyeon-environment/s3.tf)에 업무 업로드와 ALB 로그 버킷이 이미 나뉘어 있다. **[9/11 갱신]** [monitoring_s3.tf](../../infra/terraform/modules/moimyeon-environment/monitoring_s3.tf)의 `monitoring-config` 버킷은 설정 파일 전용이며 로그 저장을 명시적으로 배제한다. 로그 버킷은 이것과도 분리한다. actorId HMAC 키가 필요해지면 Sentry DSN과 같은 사전 생성 SSM SecureString ARN 참조 방식을 따른다. [waf.tf](../../infra/terraform/modules/moimyeon-environment/waf.tf)는 앱과 동일한 `log_retention_days` 변수를 사용하므로 앱 보존 기간 변경이 WAF에 파급되지 않게 해야 한다.

**대안:** 기존 업로드 버킷 재사용, 모든 로그 3년 보존, sidecar만 S3 권한을 가진다고 간주. 앞의 두 선택은 서로 다른 데이터의 정책을 묶는다. 마지막 선택은 ECS task 단위 IAM과 맞지 않는다. [ECS task IAM role](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html)

**대가:** 버킷·정책 관리 항목이 늘고 같은 task 안에서는 앱과 sidecar가 IAM role을 공유한다. 앱 코드에서 S3를 호출하지 않는 것과 앱 컨테이너에 S3 권한이 없는 것은 구별해야 한다.

**재검토:** 컨테이너 간 IAM 격리가 필수이면 독립 collector나 관리형 전달로 바꾼다. SSE-KMS의 별도 키 통제가 필요하면 비용·권한과 함께 검토한다. 90일/7일은 법정기간으로 확정한 값이 아니며 감사 대상·필드·기간은 별도 근거를 확인한다.

## DR-11. sidecar 자원은 현재 호스트에 맞춰 검증한다

**상태: 배치 용량 제약은 코드에서 확인. 해결 방식·예산은 미결정.**

**선택:** task 자원 재배분과 인스턴스 변경을 비교하고, 배포 동시 task 수를 포함한 plan·부하 실험 후 결정한다. sidecar를 기존 task에 단순 추가하는 것으로 설계를 완료하지 않는다.

**근거:** [dev/main.tf](../../infra/terraform/envs/dev/main.tf)는 `t3.small`에 API CPU 2048·메모리 1600MiB를 지정한다. 같은 파일의 주석은 JVM cold start와 DB 연결 시간 때문에 API에 2vCPU를 배정했다고 설명한다. 워커·Redis와 API rolling deployment의 배치 공간도 필요하다. **[9/11 갱신]** 모니터링 호스트는 별도 private EC2라 ECS 호스트 배치 계산에 포함되지 않는다.

**대안:** 앱 CPU/메모리를 바로 줄이기, sidecar 예약만 추가하기, 무조건 인스턴스 키우기. 각각 기존 성능 문제 재발·배치 불가·추가 비용 가능성이 있다.

**대가:** 초기 도입에 자원·부하 검증이 필요하고 FireLens가 관리형 전달보다 저렴하다는 결론은 아직 없다.

**검증:** 정상 운영과 rolling deployment에서 배치 가능 여부, cold start·readiness·p95/p99·메모리를 확인한다. 앱 자원을 줄이는 경우 기존 DB 연결 문제를 다시 시험한다. Terraform plan 없이 인프라 변경을 완료로 취급하지 않는다.

## DR-12. 그로스는 확정된 비즈니스 사건부터 시작한다

**상태: 설계 선택안. 구체 사건·SID·UTM 정책은 미결정.**

**선택:** 모임 생성·참가 확정·후기 작성처럼 서버가 확인한 성공 사건을 DB commit 이후 기록한다. SID/UTM과 사용자별 연결은 후속 슬라이스로 둔다. 기존 알림 Redis/Outbox를 일반 로그 운반용으로 재사용하지 않는다.

**근거:** [9/8 팀 회의](https://team-100-thieves.slack.com/docs/T0AUQ9XFYA0/F0C02SYQEER)는 클라이언트 화면/클릭과 서버 비즈니스 사건의 역할 분담을 검토했다. MOI-411 자체에는 SID 수명·유입 귀속·분석 이벤트 계약이 없다. DB commit 전에 성공 로그를 남기면 롤백된 작업을 전환으로 집계할 수 있다.

**대안:** 모든 요청에 전체 UTM을 붙이기, 인증 세션을 분석 SID로 재사용, 로그용 Kafka/Outbox 구축. 각각 입력·보존 범위 확대, 자격 증명과 분석 식별자 결합, 전달 복잡성을 만든다.

**대가:** 초기 로그만으로 완전한 광고 attribution을 제공하지 않는다. commit 이후 로깅에도 프로세스 종료에 따른 누락이 남아 정확한 수치는 DB 상태와 대조해야 한다.

**재검토:** FE/API 연동, 첫 유입/최근 유입 정책, 식별자 수명과 처리 목적이 정해지면 SID/UTM을 추가한다. actorId가 필요하면 HMAC-SHA-256의 128비트와 키 버전을 제안하며 원문 회원 ID·인증 토큰과 분리한다. 이는 익명화 보장이 아니고 키 회전·연결성 관리가 추가되는 선택이다. 강의의 10자리 절단을 그대로 채택하지 않았다.

## 확정값으로 사용하면 안 되는 수치

아래는 모두 설계 실험의 시작값이다. 현재 트래픽 측정이나 법적 의무에서 도출한 확정값이 아니다.

| 항목 | 제안값 | 확인할 것 |
| --- | --- | --- |
| 일반 운영 로그 보존 | S3 90일, 앱/라우터 CloudWatch 7일 | 실제 조회 기간·보존 목적, WAF/감사 정책과 분리 |
| S3 업로드 | 10MB 또는 1분, gzip·PutObject | 파일 수·PUT 비용·허용 지연·배포 시 손실 |
| Logback 큐 | 1024 이벤트, 비차단 | 실제 이벤트 참조 크기·burst·포화 손실 |
| router 메모리 | 128MiB 예약, 256MiB 제한 | API/worker별 실제 소비·호스트 배치 |
| S3 전용 디스크 버퍼 | 256MiB | bytes/sec와 견딜 장애 시간, 다른 버퍼와 합산 용량 |
| 이벤트 크기 | 전체 16KiB, 일반 문자열 256자 | 필요한 진단 정보와 비용, 유효 JSON 유지 |
| 예외 제한 | stack frame 30개, cause 깊이 5 | 원인 분석에 충분한지, 예외 폭주 비용 |

`slow` 임계값, 최대 허용 로그 손실·전송 지연, 비용 예산과 서비스 SLO 중단 기준은 아직 수치로 정하지 않았다. 배포 전 계측·운영 정책 확인이 필요하다. [미결정 사항](tbd.md)

## 이 결정 기록의 검증 범위

기존 설계 조사와 읽기 전용 QA에서 확인한 내용을 기록했다. QA가 지적한 Sentry 우회·Security 이전 요청 누락·비동기 context·router 생명주기 위험은 위 결정에 반영되어 있다. 구현·테스트·성능 실험·Terraform plan으로 선택안을 검증한 상태는 아니다.

**[9/11 갱신]** PR #123으로 Sentry 우회 위험은 코드에서 해소됐다(DR-06). 메트릭 경로(OTLP → Collector → Prometheus → Grafana)가 생겨 DR-03의 CloudWatch 역할이 "비율 집계"에서 "개별 실패 요청 조회"로 좁아졌다. 로그 전송 경로·버퍼·sidecar 자원 결정(DR-01·02·09·11)은 영향이 없다. 모니터링 스택의 dev 실제 배포·Sentry 수신은 그 PR의 배포 후 수용 조건이며 이 문서가 확인한 것이 아니다.

## 2026-09-14 보강 결정

아래는 현재 코드 `b3ae0040`과 추가 요구사항을 반영한 선택이다. ERROR 첫 발생 즉시·반복 묶음은 사용자 답변으로 확정했다. 구현·운영 합의·측정이 필요한 나머지 선택은 제안 상태다.

### DR-13. 알림 전에 WARN의 의미를 맞춘다

현재 `CoreErrorType`은 닉네임 중복·세션 만료·정원 초과 같은 예상 가능한 거절도 WARN이다. 그대로 1분 5회 경보를 붙이면 정상 사용이 경보를 만든다.

선택지는 기존 수준을 두고 일부 WARN을 알림에서 제외하거나, 사건의 의미에 맞춰 수준을 바꾸는 것이다. 후자를 제안했다. 예상된 거절은 INFO, 기능 저하·재시도·비정상 징후는 WARN, 최종 실패는 ERROR다. HTTP 상태와 errorCode는 유지한다. 기존 `ErrorType.logLevel`을 그대로 둔다는 이전 안은 이 분류에 한해 바뀐다.

요청 완료는 INFO 요약을 기본으로 하고 최종 실패 경계에서만 진단 로그를 남긴다. 실패 진단이 없는 느린 성공 요청은 완료 로그 자체를 WARN으로 올린다. 한 실패를 요청 로그와 예외 로그에서 두 번 집계하지 않기 위한 선택이다. 프레임워크 WARN까지 일괄 INFO로 낮추지는 않는다.

### DR-14. ERROR 묶음과 WARN 집계를 다른 경로에서 처리한다

S3 업로드를 기다리면 즉시 알림이 늦고, 앱의 HTTP Appender로 직접 알리면 앱이 알림 서비스의 재시도를 맡는다. 현재 이미 있는 Sentry를 ERROR 경로로 선택했다. WARN은 Sentry 기본 event 대상이 아니므로 CloudWatch metric filter·alarm·SNS를 사용한다.

ERROR는 최초·해결 후 재발과 미해결 상태의 지속 발생 규칙을 나눈다. 사용자 기준은 첫 발생 즉시·반복 묶음이며 10분 반복 간격은 제안이다. Action interval은 최소 통지 간격일 뿐 반복 스케줄러가 아니므로, 지속 규칙은 추가 오류가 들어올 때 평가한다. 두 규칙의 최초 중복 통지와 재발 억제 여부를 검증한다. 묶음은 같은 오류 이슈의 대표 재알림이고 정확한 구간 집계표는 아니다.

WARN은 environment·service·유한 eventKey별 고정 60초 구간에서 수신 사건 5회 이상을 조건으로 제안했다. task별로 쪼개지 않는다. 정확한 이동창은 별도 상태 관리가 필요하므로 미결정 사항으로 남긴다. metric filter는 전송 중복을 제거하지 않아 수신 횟수가 업무상 사건 수와 다를 수 있다. 보고서에서는 eventId로 중복을 제거한다.

근거: [CloudWatch metric filter](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/FilterAndPatternSyntaxForMetricFilters.html), [Sentry action interval](https://sentry.zendesk.com/hc/en-us/articles/21252824620571-What-does-the-Action-Interval-option-do-in-the-alert-rules).

### DR-15. FATAL은 수준을 추가하지 않고 영향으로 표시한다

Logback에는 FATAL 수준이 없다. 로깅 facade를 바꾸거나 별도 fatal API를 만드는 대신 `ERROR + impact=critical`을 선택했다. 일반 ERROR와 경보 규칙을 구별한다.

프로세스가 OOM·강제 종료되면 마지막 로그는 남지 않을 수 있다. critical 로그만 감시하는 안은 부족하므로 ECS task 종료·외부 health 신호도 감시한다. 이 인프라 경보는 기존 Grafana 대시보드가 자동 제공하는 것으로 취급하지 않는다. [Logback 수준](https://logback.qos.ch/manual/architecture.html)

### DR-16. TRACE·DEBUG는 CloudWatch 3일만 보관한다

S3에 모든 로그를 90일 저장하는 이전 제안은 TRACE·DEBUG 3일 요구와 맞지 않는다. prefix를 분리하고 debug만 3일 lifecycle을 걸어도 생성 후 늦게 업로드된 객체는 사건 시각보다 오래 보관된다.

초기 선택은 debug를 S3에서 제외하고 CloudWatch debug 그룹만 3일 보관하는 것이다. 원래 사건 시각을 CloudWatch timestamp로 전달하고 오래된 사건의 거절을 처리한다. 추가 파기 작업과 장기 복제본을 만들지 않는다. ops·growth는 S3 90일 제안으로 유지한다.

Sentry breadcrumb·보고서·공용 filesystem buffer에 debug가 우회 저장되지 않게 한다. 이를 분리할 수 없으면 Fluent Bit 공용 input은 메모리로 두고 ops·growth의 S3 plugin 전용 버퍼만 디스크를 쓴다. 그 경우 CloudWatch 장애 중 ops의 손실 위험이 늘어나는 대가를 명시했다. 저장소의 비동기 물리 삭제 시점까지 정확히 72시간이라고 약속하지 않는다. [CloudWatch 이벤트 시각과 거절](https://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_PutLogEvents.html)

### DR-17. 초기 ops 검색은 정상 요청까지 포함한다

기존 안은 정상 요청을 S3에만 두고 CloudWatch에는 실패·slow 등을 선별했다. 비용은 줄지만 방금 발생한 정상 요청과 실패 요청의 연결을 바로 볼 수 없다.

현재는 body를 제거한 요청 요약 전체와 WARN/ERROR·주요 상태 사건을 CloudWatch에 7일 보관하는 안을 선택했다. 실제 bytes와 검색 빈도를 확인한 뒤 정상 요청부터 샘플링한다. growth는 S3만 사용한다. 비율과 성능 추세는 기존 메트릭이 맡고 로그에서 같은 대시보드를 다시 만들지 않는다.

이 선택은 CloudWatch ingestion 비용을 더 쓴다. 비용 예산을 넘으면 정상 요청 샘플링으로 되돌릴 수 있으며 WARN·ERROR·필수 보안 사건부터 줄이지 않는다. DR-03의 선별 정책은 후속 최적화로 옮긴다.

### DR-18. 환경 선택과 진단 수준을 분리한다

현재 `logging.config`는 활성 프로파일 문자열을 파일명에 넣으며 staging XML이 없다. 프로파일별 XML을 더 만드는 방식은 `dev,perf` 조합 문제를 남긴다. 고정 `logback-spring.xml`에서 환경과 형식을 결정하는 방식을 선택했다.

기본 환경은 하나만 선택하고 perf는 보조 프로파일로 둔다. 여러 기본 환경이 충돌하면 시작 검증에서 실패시키되, 그 전 로깅 초기화에서는 보수적인 원문 차단 정책을 적용한다. test→local 상속은 예외로 허용하고 외부 전송 OFF를 우선한다.

DEBUG 조사는 logger·담당자·만료 시각을 기록한다. 자동 종료는 구현할 기능이지 Logback의 기본 보장이 아니다. 현재 local/local-dev의 `show_sql=true`도 공통 false로 바꾸는 대상으로 포함했다. SQL·bind·wire 출력의 우회를 방치하면 최종 출력 정제 계약이 성립하지 않는다. [Spring Boot logging](https://docs.spring.io/spring-boot/reference/features/logging.html)

### DR-19. Sentry에 연결 키를 안전하게 남긴다

현재 Sentry 필터는 이벤트를 새로 만들면서 source trace context를 버린다. sampling만 바꾸는 대안은 이 문제를 해결하지 못한다. 안전한 eventCode·errorCode·requestId·traceId·spanId를 명시적으로 보존하는 안을 선택했다.

Sentry에는 허용된 예외 정보만 보내고 일반 로그와 DEBUG breadcrumb는 보내지 않는다. 원본 MDC·contexts를 통째로 복사하지 않는다. Throwable 없는 ERROR도 eventCode로 구분하고, fingerprint에는 요청 ID를 넣지 않는다. 공통 정책은 허용된 코드·값 규칙을 공유하되 목적지별 필드 집합은 다르게 둔다.

requestId는 tracing이 없는 실행에서도 요청을 찾는 키로 사용한다. traceId는 실제 OTel context에서만 가져오며, sampling 0.0을 traceId 부재와 동일하게 보지 않는다. worker 메시지·batch 실행과 HTTP context도 별도 수명으로 다룬다.

### DR-20. 일일 보고는 앱 밖에서 실행한다

앱 scheduler는 인스턴스 수·배포·재시작의 영향을 받고, S3만 조회하면 늦게 업로드된 로그 때문에 당일 보고가 지연된다. Scheduler와 운영용 Lambda가 CloudWatch의 전날 WARN을 조회하는 안을 선택했다.

매일 09:00 KST 실행, 전날 00:00~24:00 KST를 대상으로 제안했다. 사건별 횟수·시각·서비스·배포·조회 링크만 담고 개인정보와 원문 payload는 제외한다. 날짜·환경으로 보고서를 식별한다. 쿼리 실패와 보고 누락을 0건으로 표현하지 않는다. 보고 대상은 그 시점에 CloudWatch에 도착한 기록이며 완전한 업무 원장이 아니다.

새 운영 함수와 SNS 비용이 생기지만 로그용 메시지 브로커는 필요하지 않다. 함수의 재시도·중복 통지·실패 감시와 수신 채널은 배포 계약에 포함한다. 이번 작업에서는 통지를 보내지 않았다.

### DR-21. 그로스와 감사의 내구성을 구별한다

그로스 로그를 유일한 원장으로 부른 이전 두 트랙 문서의 표현은 수정한다. commit 이후 기록해도 종료 직후 누락이 남으므로 DB 확정 상태와 대조하는 분석 자료다. eventId는 전송 중복과 업무 중복을 모두 해결하지 않는다.

관리자 개인정보 조회·내보내기·권한 변경은 필요한 행위·주체·대상·시각·결과부터 식별한다. 법적 감사 대상이면 일반 best-effort 경로, 감사 전용 수집, audit outbox를 비교하고 실패 시 작업 거부 여부까지 결정한다. 필요가 확인되지 않은 일반 로그에 DB 원장·Kafka를 먼저 넣지는 않는다.

로그 종류는 요청·오류·활동·시스템·DB·보안·배치·디버깅으로 구분하되 같은 출력 계약을 따른다. ALB/WAF·OS·DB 서버 기록은 앱 formatter 밖의 별도 수집·권한 문제로 남긴다.

## 2026-09-14 검토 결과

읽기 전용 QA에서 WARN 의미와 경보 충돌, 프로파일 조합, Sentry trace 제거, show_sql 우회, 기존 모니터링 범위를 확인했다. 초안 검토에서 추가로 발견한 Sentry 반복 규칙 누락·AsyncAppender 우선 폐기 오해·지연 DEBUG의 보존 연장을 수정했다. 실제 배포·기능 테스트·부하 실험·Terraform plan은 실행하지 않았다.

### DR-22. 추천 운영 정책을 채택하고 live를 팀원 3명이 함께 맡는다

**상태: 사용자 확정.** 사용자가 TBD 추천안을 채택하고 live 담당자를 팀원 3명 전원으로 지정했다.

| 항목 | 확정한 정책 |
| --- | --- |
| 보존 | TRACE·DEBUG는 CloudWatch 3일만. 일반 ops는 CloudWatch 7일과 S3 90일, growth는 S3 90일 |
| 로그 유실 | 일반 운영 로그는 서비스 가용성을 우선한다. 버퍼 포화·호스트 장애 때 유실을 허용한다. 무손실 감사의 원장으로 사용하지 않는다 |
| WARN | 같은 environment/service/eventKey를 고정 60초 구간으로 집계해 5회 이상이면 경보 |
| ERROR | 첫 발생·해결 후 재발은 즉시. 같은 미해결 오류는 추가 발생 시 최소 10분 간격으로 묶어 알림 |
| 일일 보고 | 매일 09:00 KST에 전날 WARN 요약 |
| slow | 일반 API는 1초부터 시작해 측정으로 조정. 업로드·LLM은 별도 기준 |
| live 담당·수신 | 팀원 3명 전원이 공동 담당이며 live 알림을 함께 받는다. dev/staging과 live 채널을 분리한다 |
| 감사 범위 결정 순서 | 실제 제공하는 관리자 개인정보 열람·다운로드·권한 변경부터 확인해 필요한 기록을 정한다 |

실제 채널 식별자와 수신 계정은 연결 단계에서 확인한다. 팀원 이름·계정을 추정해서 등록하지 않는다. 야간 응답 의무·응답 SLA와 critical의 구체적인 반복·상향 통지 규칙은 이 답변으로 정해진 것으로 보지 않는다.

보존·유실·집계 창·반복 간격은 더 이상 사용자 미결정 사항이 아니다. 저장소의 실제 만료 동작, Sentry 규칙 지원, 버퍼·sidecar 자원, slow 예외 기준은 구현·검증으로 확정한다. 추천 정책 채택을 코드 구현·배포나 외부 메시지 전송 완료로 취급하지 않는다.

### DR-23. 안전한 로깅 초기화와 환경 거부 시점을 분리한다

**상태: 설정 슬라이스 구현·테스트로 확인.** 환경 후처리기에서 바로 검증 예외를 던지면 고정 XML의 초기화에 도달하지 않는다. 여러 부팅을 같은 JVM에서 시험하면 이전 테스트의 안전 formatter가 이 공백을 가릴 수 있다.

후처리기는 고정 logging.config와 안전한 형식·외부 전송 OFF를 먼저 설치하고 검증 결과를 보관한다. 로깅 초기화 후 Context initializer가 잘못된 환경의 시작을 거부한다. 새 JVM에서 첫 부팅을 실패시키는 테스트로 순서와 최종 출력의 원문 차단을 확인했다. ConfigData 이전의 설정 파싱 실패까지 보호한다고 주장하지 않는다.

환경 후처리기의 공통 정책은 상위 logging.config로 우회할 수 없게 고정했다. 로컬 Sentry 비활성은 실제 자동설정을 포함한 테스트로 확인했다. 사건별 registry와 전송 큐는 이번 설정 테스트가 요구하지 않으므로 만들지 않았다. 현재 허용된 service.ready 외의 메시지는 안전한 일반 사건 코드로 출력한다.

### DR-24. 예제의 설정·로그 객체·정제 분리를 반영한다

**상태: 후속 사용자 요청으로 구현.** 강의 예제의 RequestLoggingConfig·RequestLogEntry·독립 mask 함수에서 각각 타입 설정, 고정 필드 로그 객체, 출력과 정제의 분리를 가져왔다. body 수집·S3 스케줄러·Slack Appender는 기존 범위와 제약 때문에 이식하지 않았다.

LoggingProperties가 slow 기준·제외 경로·예외 출력 한도를 바인딩한다. 초기 Logback 단계에서도 같은 타입을 Binder로 사용하고 잘못된 값은 안전한 기본값으로 대체한 뒤 시작을 거부한다. 프레임·깊이는 기존 안전 상한 안에서만 조정한다.

RequestLogEntry는 method·routeTemplate·status·durationMs·errorCode만 가진다. RequestLogWriter가 kotlin-logging payload에 이 타입을 전달하면 LogSanitizer가 검증된 필드를 보존한다. 사건명만 같은 임의 Map은 허용하지 않는다. 일반·실패 요약은 INFO, slow 성공은 WARN이며 제외 경로의 실패는 남긴다.

LogSanitizer는 기존 정제 규칙을 Spring 환경과 출력 형식에서 분리한 변환 코드다. SafeLogFormatter는 결과의 형식만 정한다. service.ready만 허용했던 DR-23 단계에서 요청 요약 사건 두 개를 추가한 변경이다. 자유 형식 메시지의 원문 허용은 추가하지 않았다.

경로 생성자 검사는 문법 검증이다. 등록된 MVC 패턴인지 확인하는 책임은 후속 HTTP 어댑터에 있으며 원문 URI를 넘기면 안 된다. HTTP 필터·MDC 수명 관리·AsyncAppender·S3 전송은 아직 연결하지 않았다.

### DR-25. 환경별 XML을 유지하고 공통 부분만 공유한다

**상태: 사용자 수정 요청 반영.** 사용자는 앞으로 환경별 설정이 달라지므로 환경별 Logback XML을 유지하고 dev/perf 설정을 추가하라고 지적했다. 단일 XML로 합친 것은 중복 제거를 환경별 변경 가능성보다 우선한 과도한 통합이었다. DR-18과 초기 구현의 단일 XML 선택은 이 결정으로 대체한다.

local·local-dev·test·dev·staging·live의 개별 XML과 dev/perf 전용 `logback-dev-perf.xml`을 둔다. encoder와 민감 logger 차단은 common-appenders.xml로 공유한다. 각 파일에서 수준·형식·appender 연결을 정하고 LoggingEnvironment의 수준·형식 필드는 제거했다.

파일 선택만 정규화한다. dev,perf와 perf,dev는 같은 dev-perf 파일을 사용하고 test/local 상속은 test 파일을 사용한다. 잘못된 환경은 bootstrap XML로 안전한 실패 기록을 남긴 뒤 시작을 거부한다. 안전 정책이 없는 임의 외부 XML로 우회하는 것은 계속 차단한다.

### DR-26. 구현 세부 정책을 사용자 승인으로 확정한다

**상태: 2026-09-18 사용자 승인.** 구현 과정에서 개별 확인 없이 정했던 아래 선택과 영향을 설명한 뒤 사용자가 승인했다.

| 항목 | 승인한 현재 동작 |
| --- | --- |
| 미등록 메시지 | 원문을 숨기고 application.log/application.error로 표시한다. service.ready와 타입이 있는 요청 요약은 허용하며, 기존 자유 형식 로그의 상세 진단 정보가 줄어드는 점을 수용한다 |
| 설정 우선순위 | logging.config·SQL·wire 로그 등의 안전 정책은 코드에서 최우선으로 적용한다. 명시한 환경변수·실행 인자가 덮어쓰일 수 있다 |
| 로컬 외부 전송 | local·local-dev·test의 Sentry·OTLP는 명시적으로 활성화를 요청해도 OFF로 둔다 |
| 환경과 파일 선택 | 기본 환경을 enum으로 제한하고 조합을 정규화한다. 새 기본 환경은 XML과 enum을 함께 추가한다. 수준·형식·목적지는 환경별 XML이 소유한다 |
| 설정 상한 | slow 기준 1ms~5분, 제외 경로 최대 32개·각 256자를 허용한다. 이 값은 측정 결과가 아닌 구현 정책이다 |
| 메타데이터 형식 | 최대 128자와 허용 문자 규칙을 적용하며 부적합한 값은 unknown 등의 대체값으로 기록한다. 한글 코드 위치 등이 빠질 수 있다 |
| 요청 로그 형식 | 정해진 HTTP 메서드, 제한된 경로 문법, E 뒤 숫자 3~6자리 오류 코드 등으로 검증한다. 부적합한 입력은 객체 생성 시 예외를 던진다 |

kotlin-logging 8.0.4 사용, support:logging의 api 의존으로 facade 제공, RequestLogWriter 자동 Bean 등록도 설명한 구현 방식으로 승인받았다.

이 승인은 위 정책과 현재 구현 방식의 채택이다. 테스트가 운영 적합성을 증명한 것으로 해석하지 않으며, 환경별 XML을 단일 파일로 다시 합치는 승인도 아니다. 이번 승인 기록에서 실행 코드·커밋·push·배포·외부 통지를 추가로 수행하지 않았다.

### DR-27. Servlet 처리 완료를 기준으로 요청 요약을 기록한다

**상태: 2026-09-18 요청 연결 구현.** 공통 기반을 77801ac9로 커밋한 뒤 실제 HTTP 요청의 수집을 추가했다.

2026-09-19 사용자 승인: HTTP 연결의 동작·검증 결과와 후속 범위를 보고한 뒤 승인을 받았다. 해당 구현을 로컬 커밋으로 정리한다.

필터 finally는 async와 오류 페이지의 최종 상태를 보장하지 않는다. 수동 AsyncListener 등록은 조기 complete와 여러 dispatch의 순서를 다뤄야 한다. 현재 Tomcat의 requestDestroyed가 sync 오류 페이지 처리 뒤, async completion 뒤에 호출되는 동작을 확인하고 ServletRequestListener를 완료 지점으로 선택했다. 실제 RANDOM_PORT 테스트로 직접 complete·timeout·async 실패·재디스패치·sendError를 검증했다. 네트워크 전달 완료나 클라이언트 수신 성공의 보장은 아니다.

요청 상태는 request attribute에 두고 원자적 완료 표시로 한 번만 기록한다. route는 최초 MVC 매핑 패턴을 보존하며 ERROR와 async의 후속 경로로 덮어쓰지 않는다. MVC에 도달하지 않는 OAuth·health는 고정 분류, 다른 조기 종료는 UNMATCHED다. 확장 HTTP method는 UNKNOWN으로 정규화해 엄격한 로그 객체 검증 때문에 기록이 사라지지 않게 했다.

서버 requestId는 UUID로 만들고 외부 헤더 값을 신뢰하지 않는다. 새 응답 헤더·body를 추가하지 않았다. 기존 ApiResponse와 AuthErrorWriter가 안전한 오류 코드만 전달하며 response wrapper는 스트림을 가로채거나 캐싱하지 않는다. 일반 실패 요약 INFO와 기존 Advice 진단 로그의 역할을 유지한다.

필터는 observation 다음·Security 이전에서 동작한다. 완료 로그는 캡처한 HTTP span을 사용하고 핸들러의 자식 span과 trace ID로 연결한다. sampling=0에서 실제 ID가 존재함을 확인했으며 span ID까지 같다고 잘못 가정한 테스트는 scope에 맞춰 수정했다. 필터·완료 리스너의 MDC는 이전 값을 복원한다. 일반 Callable/@Async 작업 내부로의 전파는 다음 범위다.

### DR-28. 로그 저장은 ECS 라우터가 맡는다

**상태: 2026-09-19 S3 저장 슬라이스 구현. AWS 적용 전.** 앱 SDK 업로드는 요청·스케줄러가 저장소 장애를 떠안는다. 기존 ECS EC2에 FireLens를 붙이면 앱은 stdout 계약만 지키면 된다. 설정 전용 모듈을 추가하고 Kotlin 모듈에 AWS 의존성을 넣지 않았다.

운영 로그는 CloudWatch 7일과 S3 90일, DEBUG/TRACE는 CloudWatch 3일로 나눈다. S3는 PutObject로 gzip 파일을 묶어서 쓴다. multipart의 권한·정리 절차를 늘리지 않는 대신 작은 파일 수와 요청 비용을 부담한다. 10MiB·1분은 초기 설정이며 비용 측정 결과가 아니다. growth 경로는 준비하지만 그로스 발행기는 이번 구현에 없다.

라우터를 essential로 두면 종료 즉시 앱 task도 종료된다. 일반 로그는 손실을 허용하기로 했으므로 non-essential + 재시작을 택했다. 시작할 때는 라우터 엔진의 HEALTHY를 기다린다. 시작 뒤 실패는 앱을 직접 중단하지 않고 버퍼 포화 후 로그를 잃는다. S3 도착 감시가 따로 필요하며, 재시작 정책만으로 모든 장애를 복구한다고 보지 않는다.

라우터 128MiB·CPU 64 shares, 드라이버 계획 여유 32MiB를 잡았다. API/Worker의 앱 메모리를 줄이지 않고 task 메모리를 각각 1760/928MiB로 올린다. 실제 EC2 배치·blue/green 여유·부하 때 손실량은 dev 배포 후 확인한다. 현재 수치를 운영 적정 용량으로 확정하지 않는다.

### DR-29. 라우터를 되돌려도 설정 파일과 짧은 보존 정책을 지킨다

**상태: 2026-09-19 구현·읽기 전용 리뷰 반영.** 설정 객체를 매번 같은 키로 덮거나 이전 hash 키를 지우면 이전 task ARN이 다른 설정을 읽거나 시작에 실패한다. revision별 파일을 추가하고 객체 키에 내용 hash를 넣는다. 이전 revision 객체와 읽기 권한을 유지하며 prevent_destroy로 실수로 지우는 plan을 막는다. 버킷 versioning만으로는 이 조건을 대신할 수 없다.

이미 저장소를 만든 환경은 disabled로 되돌리지 않는다. provision 모드로 라우터만 빼고 S3·설정·권한을 남긴다. 이때 모든 로그를 별도 CloudWatch fallback 그룹에 non-blocking으로 보내 3일 보존한다. 기존 30일 그룹으로 보내면 DEBUG 보존 약속을 깨므로 선택한 비상 우회다. 그동안 ops의 7일·S3 보관은 제공하지 않는다는 대가가 있다. 도입 전 exact task ARN 복귀는 당시 정책까지 복원하므로 별도로 구분한다.

CI plan이 새 설정 객체를 refresh하려면 HeadObject/GetObjectTagging 권한이 필요하다. Shared plan 역할에 dev 설정 revision prefix만 허용했다. 로그 데이터 읽기는 열지 않았다. 기존 파이프라인의 shared 적용 → dev plan 순서를 그대로 사용한다. live 활성화 때는 해당 환경의 설정 refresh 권한도 별도로 검토한다.

구체 설정·검증·현장 확인은 [저장 모듈 README](../../infra/terraform/modules/application-logging/README.md)에 둔다.


### DR-30. stable 태그보다 지원 계열과 실패 복구 검증을 함께 본다

**상태: 2026-09-19 구현 검증.** AWS stable 표시는 당시 2.34.3.20260918이었지만, AL2 기반 2.x와 AL2023 기반 3.x의 지원 정책은 다르다. 또한 2.x에서 S3 장애 중 강제 종료 후 복구한 객체의 JSON 파싱 실패를 재현했다. stable 표시만으로 이미지를 정하면 이 경로를 놓친다.

실제로 배포된 3.4.17을 digest로 고정하고 같은 테스트를 통과한 것을 확인했다. 문서에 나온 3.4.18은 확인 시점의 public ECR에서 조회되지 않아 선택하지 않았다. 테스트는 버퍼가 남아 있는 컨테이너 재시작에 한정하며 task·호스트 소실을 보장하지 않는다. [AWS 배포 버전·지원 지침](https://github.com/aws/aws-for-fluent-bit#consuming-aws-for-fluent-bit-versions)을 함께 확인했다.


### DR-31. API와 Worker가 소비하는 라우터 정책을 한곳에 둔다

CPU 64·추가 메모리 값은 이미 수집 모듈에서 공유했지만, 이를 task에 적용하는 산식과 mode 분기를 각 ECS 파일에 반복하고 있었다. 서비스별 원래 예산·기존 로그 그룹을 입력으로 받는 `logging_task_policy` local map을 환경 모듈에 둔다. CPU 차감·task 메모리·로그 경로·HEALTHY 의존성·sidecar·볼륨·예산 유효성은 이 map이 소유한다. 리소스 주소·고유 앱 설정·precondition 블록은 각 ECS 리소스에 남긴다.

수집 모듈은 기존 테스트로 검증하고, 소비 측은 별도 모듈 출력 fixture의 96 CPU·192MiB·추가224MiB를 실제 task JSON에 적용하는지 확인한다. 기본값 64/160을 다시 하드코딩하는 회귀도 잡기 위한 선택이다. AWS provider·metadata는 mock만 사용한다. refactor 전후 같은 테스트 6개가 통과하고 10개 planned task의 CPU·memory·전체 container JSON·volume이 동일함을 확인했다. 실제 AWS plan이나 배치 능력을 이 비교로 대신하지 않는다.
