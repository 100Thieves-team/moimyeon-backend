# MOI-411 로깅 설계 재정리: 서비스 추적 로그와 그로스 로그

> 2026-09-14: 이 문서는 9/11 설계 이력이다. 현재 기준은 [보강 설계](design.md)와 [DR-13~21](decisions.md)이다. DEBUG는 S3에 저장하지 않으며 초기 ops는 CloudWatch에 요청 요약 전체를 보낸다. 그로스 로그는 DB 확정 상태와 대조할 분석 자료다. 아래의 ‘유일한 출처·원장’ 표현을 내구성 보장으로 해석하지 않는다.

2026-09-11. [설계 제안](design.md)(9/9)과 [결정 기록](decisions.md) DR-01~12를
"서비스 추적 트랙"과 "그로스 트랙" 두 축으로 다시 묶은 문서다. 새 결정을 추가하지 않았고,
팀원의 PR #123(dev Grafana·Prometheus·Sentry, MOI-516)이 바꾼 사실은 4절에 정리했다. 설계 제안서와 결정 기록에도 같은 내용을 `[9/11 갱신]`으로 반영했다. 구현·인프라 변경은 하지 않았다.

근거 구분: **[이슈]** MOI-411 본문, **[제약]** 사용자가 지정한 조건, **[회의]** 9/8 팀 회의,
**[코드]** 저장소에서 확인, **[제안]** 설계 선택안.

## 1. 해결하려는 문제

### 1.1 두 트랙이 공유하는 문제 (파이프라인)

| ID | 문제 | 근거 |
| --- | --- | --- |
| P1 | 로그가 S3에 남지 않는다. 현재 경로는 stdout → awslogs → CloudWatch 30일뿐이다. | [이슈] [코드] `ecs.tf` `logDriver = "awslogs"` |
| P2 | 로그가 컬러 텍스트 한 줄이라 기계가 읽을 수 없다. 조회·집계·필드 필터가 불가능하다. | [코드] `logback-dev.xml`·`logback-live.xml` pattern encoder |
| P3 | 기존 로그 호출이 원문을 흘린다. 예외 메시지(`ApiControllerAdvice`, `AsyncExceptionHandler`), 회원 ID·외부 URL(`ResumeService`, `JsoupOpenGraphClient`). 이력서·후기·LLM 입출력을 다루는 서비스라 자유 텍스트가 예외 메시지에 실릴 수 있다. | [코드] |
| P4 | 앱이 S3 SDK·업로드 스케줄러를 가지면 안 된다. 참고 구현 `swm-secure-logger`의 `S3LogArchiveService`는 이 제약을 위반한다. Kafka·ELK도 지양. | [제약] |
| P5 | 로그 저장 장애가 서비스 장애로 번지면 안 된다. | [제약]에서 파생, [제안] |
| P6 | 보존 기간 변수를 WAF와 공유해서 앱 보존을 바꾸면 WAF 보존도 바뀐다. | [코드] `variables.tf`·`waf.tf` `log_retention_days` |
| P7 | kotlin-logging이 도입되어 있지 않다. 모든 호출부가 `LoggerFactory.getLogger`다. | [이슈] [코드] |

### 1.2 서비스 추적 트랙 고유 문제

| ID | 문제 | 근거 |
| --- | --- | --- |
| T1 | 요청 단위 완료 기록이 없다. status·duration·route 기준 통계와 접근 오류율을 낼 수 없다. | [코드] |
| T2 | OAuth·401·403은 MVC 이전 Security 필터에서 끝난다. interceptor만으로 기록하면 이 요청이 빠진다. | [코드] `docs/conventions/auth.md` |
| T3 | traceId가 요청 로그·예외 로그·`@Async`·worker에 실제로 이어지는지 검증되지 않았다. tracing sampling은 0.0이고 `AsyncConfig`는 수동 executor다. | [코드] `monitoring.yml`, `AsyncConfig.kt` |
| T4 | 한 예외를 계층마다 다시 기록하면 저장량과 분석 중복이 늘어난다. | [제안] |
| T5 | 최근 장애의 즉시 검색과 전체 보관의 비용을 따로 조절해야 한다. | [제안] |

### 1.3 그로스 트랙 고유 문제

| ID | 문제 | 근거 |
| --- | --- | --- |
| G1 | FE 클릭·화면 이벤트와 서버 비즈니스 사건의 역할 분담이 필요하다. 서버는 "확정된 결과"를 맡는 방향이다. 도구는 미확정. | [회의] |
| G2 | 성공 사건을 DB commit 전에 기록하면 롤백된 작업이 전환으로 집계된다. | [제안] |
| G3 | 분석 로그에 원문 회원 ID·인증 토큰을 넣을 수 없다. SID·UTM·유입 귀속 계약이 없다. | [제약] [회의] |
| G4 | 이 로그를 어디까지 신뢰할지 정해야 한다. 과금·정산 원장이 아니다. | [제안] |

## 2. 설계안: 파이프라인 하나, 출력 계약 둘

### 2.1 공통 파이프라인

```
kotlin-logging → SLF4J/Logback (SafeJsonLogFormatter, 허용 필드·예외 정제)
  → JSON stdout (유한 비차단 큐)
  → ECS FireLens / Fluent Bit sidecar (로컬 버퍼)
      → S3 (전체, gzip, 90일 제안)  → Athena
      → CloudWatch (WARN/ERROR·실패·slow·선정 사건, 7일 제안)
```

두 트랙 모두 이 경로를 그대로 탄다. 트랙은 전송 경로가 아니라 **이벤트 스키마와 기록 지점**으로 갈린다.

이 로그 경로 옆에 이미 두 경로가 더 있다(PR #123). 메트릭은 API·Worker → OTLP/HTTP → private Collector → Prometheus → Grafana, 오류 이벤트는 `SentryPrivacyFilter` → Sentry SaaS다. 세 경로는 service·environment·release 식별자를 공유해야 서로 대조할 수 있다.

| 공통 문제 | 대응 결정 |
| --- | --- |
| P1 S3 미저장 | DR-01 앱은 stdout까지, DR-02 FireLens sidecar |
| P2 텍스트 로그 | DR-05 고정 사건명 + 허용 필드 JSON formatter |
| P3 원문 유출 | DR-04 body 미수집, DR-05 message·MDC·throwable까지 정제 |
| P4 앱 SDK 금지 | DR-01 업로드·재시도는 Fluent Bit |
| P5 저장 장애 격리 | DR-09 비차단 유한 버퍼, router `essential=false` |
| P6 보존 변수 공유 | DR-10 로그 전용 버킷·별도 보존 변수 |
| P7 kotlin-logging | DR-07 직접 쓰는 모듈에 facade 의존만 추가 |
| sidecar 자원 | DR-11 dev API가 t3.small 2vCPU 전부 예약. plan·부하 실험 후 결정 |

### 2.1a 세 경로의 책임 분담: Sentry, Grafana, 로그

PR #123으로 Sentry(오류 이벤트)와 Grafana(메트릭)는 코드가 머지됐다. MOI-411이 만드는 것은 세 번째 경로인 **로그**이며, 서비스 추적 트랙과 그로스 트랙은 둘 다 이 경로 위에 얹힌다.

| | Sentry | Grafana (Prometheus) | 로그 (MOI-411) |
| --- | --- | --- | --- |
| 상태 | 코드 머지, dev 배포는 SSM 준비 후 CI apply 대기 | 코드 머지, 동일 | 설계 중 |
| 단위 | 예외 1건 → 이슈로 그룹화 | 15초마다 집계된 수치 | 사건 1건 (요청 1건, 비즈니스 결과 1건) |
| 답하는 질문 | 새 배포 후 새 종류의 예외가 생겼나, 어느 코드 라인에서 얼마나 자주, 어느 릴리스부터 | 지금 정상인가, 언제부터 느려졌나, 5xx 비율, 워커가 밀리나, 앱이 살아 있나 | 이 traceId의 요청은 route·status·errorCode가 뭐였나, 실패한 그 요청 찾기, 모임 생성이 며칠간 몇 건 |
| 보존 | SaaS 정책 | 7일 또는 8GB | S3 90일, CloudWatch 7일 (제안) |
| 담는 것 | 예외 타입, 스택 코드 위치, service·env·release 태그 | http.server.requests, JVM, DB pool, Worker 처리 결과, heartbeat | 허용 필드만. traceId, route, errorCode, eventId, 가명 actorId |
| 일부러 안 담는 것 | 예외 메시지, 요청, 사용자, MDC, 일반 로그 | 요청·사용자 단위 label, 예외 원인 | body, 헤더, 원문 ID, 자유 텍스트 |
| 트랙 | 서비스 추적 (예외 알림·그룹화) | 서비스 추적 (비율·추세) | 서비스 추적 (개별 진단) + 그로스 (유일한 출처) |

로그가 채우는 빈자리는 두 가지다. 첫째, **개별 사건 기록**. Grafana는 집계이고 Sentry는 예외 타입뿐이라 "그 요청이 뭐였나", "모임 생성이 몇 건"은 지금 어디에도 없다. 둘째, **S3 보관**. 이슈 요구사항이며 두 경로 모두 S3를 쓰지 않는다. 그로스 집계의 원장도 여기다.

그로스 트랙은 로그만 쓴다. Sentry는 오류 전용이고, Grafana는 7일 보존에 개별 사건·사용자를 담을 수 없어 전환 집계의 원장이 될 수 없다.

경계 규칙 세 가지. Sentry에 일반 로그를 흘리지 않는다(`SENTRY_LOGS_ENABLED=false`, 허용 코드는 `service.ready` 하나). Grafana에 사용자·이벤트 단위 label을 넣지 않는다. 로그로 비율 대시보드를 다시 만들지 않는다.

세 경로를 잇는 키는 service·environment·release다. Sentry와 로그를 traceId로 직접 잇는 설정은 현재 없다. tracing이 꺼져 있어 Sentry 이벤트에 traceId가 붙지 않으므로, 지금은 릴리스·예외 타입·시각으로 좁힌 뒤 로그를 찾는 흐름이다. traceId를 Sentry 태그로 붙일지는 미결이다.

양방향으로 주고받는 것이 하나씩 있다. 팀원 작업이 DR-06(Sentry 원본 유출 차단)을 대신 끝냈으므로 이 설계는 stdout 정제에 집중한다. 반대로 Sentry에 메시지가 없어 "Throwable 없는 ERROR를 구분할 안전한 오류 코드"가 QA 후속 권고로 남았는데, 이 설계의 고정 사건명·errorCode가 그 답이다.

### 2.2 트랙별 계약

| 축 | 서비스 추적 트랙 | 그로스 트랙 |
| --- | --- | --- |
| 목적 | 장애 진단, 성능, 접근 오류율 | 전환·퍼널·유입 집계 |
| 이벤트 단위 | HTTP 요청 1건 = `http.request.completed` 1건. 예외 진단은 최종 처리 경계에서 1건 | 서버가 확인한 비즈니스 결과 1건. 예: `room.created`, `room.application.accepted`, `review.created` |
| 기록 지점 | Security 체인을 감싸는 outer filter(완료 요약) + 예외 최종 처리 Advice(스택). DR-08 | DB commit 이후. DR-12 |
| 핵심 필드 | method, route template, status, durationMs, traceId, errorCode/errorType, release | eventId, occurredAt, traceId, 대상 리소스 ID, 결과 코드 |
| 식별자 | traceId/spanId (OTel). actorId는 선택 | eventId (중복 제거 키). actorId는 HMAC 가명. SID/UTM은 후속 |
| 제외 | body, query, 헤더·쿠키·IP·UA 원문, 전체 URL, raw URI fallback | 원문 회원 ID, 인증 세션·refresh token, 임의 UTM 값 |
| 조회 | CloudWatch Insights(선별, 7일) + Athena(S3, 90일) | Athena(S3). eventId로 dedup 후 집계, DB 확정 상태와 대조 |
| 손실 허용 | best-effort. ERROR도 유실 가능 | best-effort. 무손실 요구 시 별도 outbox 계약. 기존 알림 Outbox 재사용 금지 |
| 코드 위치 | `support:logging` formatter, `core-api/api` 필터·interceptor, 기존 Advice·서비스 호출부 정리 | 각 도메인의 성공 경계. 구체 위치는 미결 |
| 슬라이스 | 1차 | 2차 (사건명 합의 후) |

### 2.3 트랙이 공유하는 규칙

- 같은 formatter, 같은 허용 목록, 같은 크기 제한(문자열 256자, 프레임 30, cause 5, 전체 16KiB)을 쓴다. 그로스 이벤트라고 필드 검증을 느슨하게 하지 않는다.
- S3 객체 키는 `env/service/dt/hour` 파티션이다. 트랙은 `event` 필드로 구분한다. actorId·SID는 파티션 키로 쓰지 않는다.
- Sentry는 stdout과 별개 경로다. 두 트랙 모두 Sentry로 원문이 나가지 않아야 한다. 9/11 기준으로 `SentryPrivacyFilter`가 이를 이미 보장한다. 4절 참고.
- service·environment·release는 `OTEL_SERVICE_NAME`·`DEPLOYMENT_ENVIRONMENT`·`APP_RELEASE`에서 읽는다. 메트릭 resource attribute와 Sentry 태그가 같은 값을 쓴다.
- 정규식 마스킹은 정제된 문자열의 추가 방어이지 허용 목록의 대체물이 아니다.

## 3. 결정 기록과 트랙 매핑

| 결정 | 트랙 | 한 줄 |
| --- | --- | --- |
| DR-01 | 공통 | 앱은 stdout까지, 전송은 Fluent Bit |
| DR-02 | 공통 | FireLens sidecar 우선, Firehose 대안 유지 |
| DR-03 | 서비스 추적 | S3 전체 + CloudWatch 선별 |
| DR-04 | 서비스 추적 (그로스도 준수) | body 대신 허용 필드 요청 요약 |
| DR-05 | 공통 | 정제 경계는 최종 출력 이벤트 전체 |
| DR-06 | 공통 | Sentry 원본 경로 차단. **4절에서 갱신** |
| DR-07 | 공통 | 기존 support:logging·OTel 활용. **4절에서 갱신** |
| DR-08 | 서비스 추적 | 요청 완료 로그와 예외 진단 로그의 지점 분리 |
| DR-09 | 공통 | 비차단·유한 버퍼·유실 허용 |
| DR-10 | 공통 | 로그 전용 버킷·최소 권한·별도 보존 |
| DR-11 | 공통 | sidecar 자원은 plan·실험 후 결정 |
| DR-12 | 그로스 | 확정 사건부터, commit 이후, SID/UTM 후속 |

## 4. PR #123(dev 모니터링 스택) 이후 달라진 사실

설계는 9/9 기준이다. 9/11에 팀원이 `a5ced207`(앱 OTLP 계측·Sentry)과 `27fc4b2d`(dev 모니터링 인프라)를 PR #123으로 머지했다. 워크로그는 `.worklog/dev-monitoring-stack/`, 배포 소스는 `infra/observability/`다. 그 PR은 Loki·Tempo·FireLens·awslogs 변경을 명시적으로 제외했으므로 **로그 전송 경로 결정(DR-01·02·09·11)에는 영향이 없다.**

### 4.1 추가된 것

| 구성 | 내용 | 두 트랙에 주는 의미 |
| --- | --- | --- |
| 메트릭 경로 | API·Worker Micrometer → OTLP/HTTP(4318) → Collector → Prometheus(7일/8GB) → Grafana `dev-overview`. dev 전용 private EC2 t3.small, 암호화 gp3 20GiB, SSM 터널 접근 | 요청량·p95·5xx 비율·JVM·DB pool·Worker 처리량은 메트릭이 맡는다. 서비스 추적 로그로 같은 집계를 다시 만들지 않는다 |
| Collector 파이프라인 | metrics만. logs·traces receiver 없음 | 로그는 Collector로 보내지 않는다. 설계의 FireLens 경로가 유일한 로그 경로다 |
| Sentry | logback Appender 제거. `SentryPrivacyFilter`가 beforeSend·beforeBreadcrumb·beforeSendLog에서 허용 목록으로 재구성. 예외 메시지 고정 문자열, 예외 타입·코드 위치·태그만. Sentry Logs는 `service.ready`만 허용. dev는 `SENTRY_ENABLED=true`, `SENTRY_LOGS_ENABLED=false`, DSN은 SSM ARN 참조 | DR-06 "비활성화"는 충족됐다. 두 트랙 모두 Sentry로 원문이 나가지 않는다. 오류 그룹화는 쓸 수 있으나 메시지가 없어 원인 파악은 로그의 errorCode·traceId가 필요하다 |
| 식별자 환경변수 | `OTEL_SERVICE_NAME`·`DEPLOYMENT_ENVIRONMENT`(Terraform), `APP_RELEASE`(deploy workflow) | 로그의 service·environment·release는 이 값을 그대로 읽는다. 로그만의 식별자를 만들지 않는다 |
| SLO 버킷 | `http.server.requests` histogram 100ms·300ms·500ms·1s·3s | 서비스 추적의 `slow` 임계값은 이 버킷 중 하나와 맞춘다 |
| `monitoring-config` S3 | 설정 파일 전용, 로그 저장 배제 명시 | DR-10 로그 버킷은 이것과도 분리 |
| SSM SecureString 관례 | `/moimyeon/dev/<service>/SENTRY_DSN`, 값은 Terraform이 읽지 않고 ARN만 참조 | 그로스 actorId HMAC 키가 필요해지면 같은 방식 |

### 4.2 설계 문서에서 고친 서술

| 항목 | 9/9 서술 | 9/11 사실 | 반영 |
| --- | --- | --- | --- |
| DR-06 | SENTRY Appender가 있어 비활성화 제안. "초기에는 Sentry 오류 그룹화에 의존 못 함" | Appender 삭제, 허용 목록 필터 경로로 대체. 오류 그룹화는 가능 | DR-06을 "허용 목록 필터 경로만 유지"로 다시 씀. 사건명 레지스트리 단일화를 미결로 추가 |
| DR-07 | `support/logging`에 OTel starter | OTel starter는 `support:monitoring`. `support:logging`은 Sentry만 | 근거 수정. formatter를 `support:logging`에 두는 결정은 유지 |
| T3 traceId | 기존 OTel 기반 활용 | sampling 0.0, trace·log export off. logback pattern은 `%X{traceId}`를 이미 참조 | sampling 0에서 MDC traceId가 채워지는지가 첫 검증. 안 되면 sampling 정책이 선행 결정 |
| DR-03 | CloudWatch 선별 검색 | Grafana가 비율 대시보드를 이미 제공 | CloudWatch 역할을 "개별 실패 요청 조회"로 좁힘. Loki는 검색 대안으로만 재검토 |
| DR-11 | dev API가 t3.small 2vCPU 예약 | 모니터링 호스트는 별도 EC2 | sidecar 배치 계산에 영향 없음을 명시 |
| ecs.tf, kotlin-logging | awslogs 단일 컨테이너, kotlin-logging 미도입 | 동일 | 변경 없음 |

### 4.3 아직 검증되지 않은 것

모니터링 스택의 dev 실제 배포, EBS 재연결, Sentry SaaS 실제 수신은 그 PR의 배포 후 수용 조건이다. 이 문서는 코드와 Terraform 정의만 확인했다. QA 후속 권고로 "Throwable 없는 ERROR를 구분할 안전한 오류 코드 설계"가 남아 있으며, 이 설계의 고정 사건명·errorCode가 그 해법이 될 수 있다.

## 5. 미결정 사항 (트랙별)

### 공통

- 일반 로그 보존: S3 90일·CloudWatch 7일은 시작값. WAF·감사 보존과 분리.
- 유실 허용 범위: 일반 운영 로그는 유실 가능. 무손실 감사가 필요하면 별도 계약.
- router 자원: task 재배분 vs 인스턴스 변경. plan·부하 실험 후.
- IAM 격리: 같은 task의 앱·sidecar는 role 공유. 컨테이너 단위 격리가 필수면 전송 구조 재검토.
- Sentry 허용 목록(`SAFE_LOG_EVENTS`)과 stdout formatter 허용 목록을 한 사건명 레지스트리로 관리할지. (4절에서 새로 드러남)

### 서비스 추적

- 즉시 검색 범위: 비율 집계는 Grafana가 맡으므로 CloudWatch는 개별 실패 요청 조회로 좁힐지, 초기 유입량이 작으면 요청 요약 전체를 7일 보낼지.
- `slow` 임계값을 `http.server.requests` SLO 버킷(100ms·300ms·500ms·1s·3s) 중 무엇과 맞출지. 허용 전송 지연, SLO 중단 기준 수치.
- traceId 생성·전파 검증 결과에 따른 sampling 정책.

### 그로스

- 1차 사건 목록. 후보는 모임 생성·참가 확정·후기 작성이며 실제 성공 경계에 맞춰 이름을 정한다.
- commit 이후 기록의 구현 위치. Service 반환 직후인지 트랜잭션 완료 훅인지 정하지 않았다.
- actorId 도입 시점. 첫 슬라이스에서 생략 가능. 도입 시 HMAC 키 회전·수명 정책.
- SID·UTM: FE 접점, 첫 유입/최근 유입 정책, 허용 캠페인 값, 처리 목적. 별도 슬라이스.

### 세 경로 연결

- Sentry 이벤트에 traceId를 태그로 붙여 로그와 직접 이을지. 현재 tracing off라 자동으로는 붙지 않는다.
- 그로스 이벤트를 S3에서 별도 prefix로 나눌지, `event` 필드로만 구분할지. Athena 테이블 분리와 직결된다.

## 6. 구현·검증 순서 (트랙 표시)

1. **[공통] 출력 안전성**: 위험 호출부 정리, kotlin-logging, JSON formatter. raw canary(비밀번호·이메일·토큰·URL)를 message/argument/MDC/KV/throwable cause에 넣고 최종 출력 bytes에 없는지 검증. 같은 canary를 `SentryPrivacyFilterTest`에도 추가해 두 경로를 한 기준으로 검증. Sentry 경로 정리 자체는 PR #123으로 완료.
2. **[서비스 추적] 요청 연결**: 성공·400·401·403·404·500·OAuth·health·비동기에서 최종 status·route·traceId가 요청당 한 번 나오는지 검증. traceId가 실제로 채워지는지 여기서 확정.
3. **[공통] 전송 실험**: 고유 eventId로 stdout → S3 → Athena 확인. S3 거부·router kill·버퍼 포화·배포 종료·task 교체 각각 시험. p95/p99·메모리 전후 비교.
4. **[공통] 인프라 반영 준비**: 버킷·IAM·보존 변수 분리·router 이미지·ECS 자원의 Terraform plan. apply는 별도 절차.
5. **[공통] dev 제한 적용 후 전환 판단**: canary 노출은 즉시 중단. awslogs revision으로 복원 가능하게 유지.
6. **[그로스] 사건 추가**: 합의된 최소 사건부터 commit 이후 연결. SID/UTM·감사 로그는 요구사항 확정 후.

무손실 감사 요구, sidecar로 인한 EC2 증설 비용, 정상 요청 즉시 검색 빈도가 현재 가정을 뒤집는 조건이다.
