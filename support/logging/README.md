# 공통 로깅 설정

실행 모듈은 `support:logging`과 `logging.yml`을 조립한다. 이 모듈은 kotlin-logging facade를 `api` 의존으로 제공하고 Logback·Sentry 구현은 내부 의존으로 둔다. 외부 클라이언트나 security 모듈이 facade만 필요하면 kotlin-logging을 직접 의존한다.

환경별 `logback-*.xml`을 유지한다. 각 파일에서 로그 수준·출력 형식·appender 연결을 정하고, 공통 STDOUT encoder와 민감 logger 차단만 `common-appenders.xml`로 공유한다. 환경별 목적지가 달라지면 해당 XML에서 독립적으로 변경한다.

`logging.yml`은 `classpath:logback/logback-${moimyeon.logging.profile:local}.xml`을 사용한다. 후처리기가 활성 프로파일을 정규화해 파일 선택값을 정하며, 수준과 출력 형식은 코드에서 지정하지 않는다.

| 프로파일 | 설정 파일 | 출력·앱 기본 수준 | 외부 관측 전송 |
| --- | --- | --- | --- |
| 없음, local | logback-local.xml | 정제된 텍스트·DEBUG | OFF |
| local-dev | logback-local-dev.xml | 정제된 텍스트·INFO | OFF |
| test, test/local, test/perf | logback-test.xml | 정제된 텍스트·WARN | OFF |
| dev | logback-dev.xml | JSON Lines·INFO | 기존 Sentry·메트릭 설정 유지 |
| dev/perf, perf/dev | logback-dev-perf.xml | JSON Lines·INFO | dev 전송 설정 유지 |
| staging | logback-staging.xml | JSON Lines·INFO | 기존 Sentry·메트릭 설정 유지 |
| live | logback-live.xml | JSON Lines·INFO | 기존 Sentry·메트릭 설정 유지 |

`dev,perf`와 `perf,dev`는 같은 전용 파일을 선택한다. 현재 수준이 dev와 같더라도 perf 설정은 따로 유지해 부하 테스트의 출력 정책을 독립적으로 조정할 수 있다.

기본 환경은 하나만 둔다. `dev,perf`, `test,perf`처럼 보조 프로파일을 함께 사용할 수 있다. test가 local을 상속하는 경우만 두 기본 프로파일을 허용한다. `dev,live`, `test,live`처럼 서로 다른 환경은 시작을 거부한다. `DEPLOYMENT_ENVIRONMENT`를 지정했다면 선택한 환경과 일치해야 한다.

`LoggingEnvironmentPostProcessor`는 ConfigData 로드 뒤, 로깅 초기화 전에 환경에 맞는 XML과 안전 정책을 적용한다. 환경이 잘못됐으면 `logback-bootstrap.xml`과 외부 전송 OFF를 먼저 선택하고, 안전한 로깅 초기화 뒤 `LoggingEnvironmentInitializer`에서 시작을 거부한다. local·local-dev·test에서는 Sentry와 OTLP 전송을 명시적으로 요청해도 끈다. Hibernate의 직접 stdout 출력과 SQL·bind·HTTP wire logger는 공통으로 차단한다.

이 정책은 ConfigData를 읽은 뒤 적용된다. YAML 파싱이나 설정 파일 로드 자체가 실패한 경우처럼 그 이전 단계의 부팅 로그까지 정제한다고 보장하지 않는다. 비밀값을 설정 파일에 직접 쓰지 않는 기존 원칙을 유지한다.

## 이번 설정에서 출력하는 정보

서비스·환경·릴리스·시각·수준·logger와 유효한 traceId/spanId를 기록한다. 예외는 제한된 타입과 코드 위치만 남긴다. 자유 형식 메시지, 포맷 인자, 임의 MDC와 key-value, 예외 메시지·suppressed 원문은 출력하지 않는다. 로컬도 같은 정제 규칙을 쓴다.

허용된 사건은 `service.ready`와 타입이 있는 요청 요약이다. `RequestLogWriter`에 `RequestLogEntry`를 전달하면 `http.request.completed` 또는 `http.request.slow`로 기록하고 method·route·status·durationMs·errorCode를 보존한다. 요청 사건명만 붙인 임의 Map은 통과하지 않는다. 나머지 자유 형식 로그는 `application.log` 또는 `application.error`로 표시한다.

`LogSanitizer`는 Spring 환경 조회나 전송 없이 이벤트를 정제한다. `SafeLogFormatter`는 정제 결과를 JSON 또는 텍스트로 출력한다. 두 형식은 같은 필드 정책을 따른다.

## 요청 요약과 설정

`LoggingProperties`는 `@ConfigurationProperties`로 바인딩하고 `RequestLogWriter`에 주입한다. Logback은 일반 Bean보다 먼저 시작하므로 초기 출력에 필요한 값은 같은 타입을 Binder로 먼저 읽는다. 잘못된 값은 안전한 기본값으로 로깅을 초기화한 뒤 시작을 거부한다.

```yaml
moimyeon:
  logging:
    policy:
      slow-request-threshold: 1s
      excluded-paths:
        - /actuator/health
        - /health
        - /favicon.ico
      max-stack-frames: 30
      max-exception-depth: 5
```

slow 기준은 1ms~5분, 프레임은 0~30개, 예외 체인은 루트 포함 1~5개로 제한한다. 프레임·깊이의 안전 상한을 설정으로 늘릴 수 없다. 제외 경로는 최대 32개이며 `/health`를 지정하면 해당 경로와 하위 경로만 제외하고 `/healthcare`는 남긴다. 제외 경로라도 HTTP 400 이상인 요청 요약은 기록한다.

일반 요청과 실패 요약은 INFO다. 실패의 최종 진단은 핸들러가 맡아 중복 경보를 막는다. HTTP 400 미만인 요청이 slow 기준 이상이면 완료 요약 자체를 WARN으로 기록한다.

`RequestLogEntry.routeTemplate`에는 서버가 등록한 MVC 매핑 패턴을 전달해야 한다. 생성자는 문법·길이·상태·시간·오류 코드를 검증할 뿐, 원문 URI를 안전한 템플릿으로 변환하지 않는다. 예를 들어 숫자 경로 조각이 개인정보인지는 문자열 검사로 판별할 수 없다. 매핑이 없는 요청은 `UNMATCHED`를 사용한다. 실제 Servlet 필터와 이 연결을 검증하는 작업은 다음 슬라이스다.

기록 예시는 다음과 같다. body·쿠키·임의 헤더를 받을 필드는 없다.

```kotlin
writer.write(RequestLogEntry("POST", "/v1/rooms/{roomId}", 201, 42))
```

Sentry는 기존 `SentryPrivacyFilter`를 계속 사용한다. JSON encoder의 정제가 Sentry에 자동 적용되는 것은 아니다. 이번 변경은 Sentry의 새 이벤트 필드나 fingerprint를 추가하지 않는다.

S3·FireLens·로그 보존·알림과 HTTP 요청 필터, 비동기 context 전파는 후속 범위다. 요청 요약 작성기 자체는 구현했고 자동 요청 수집은 아직 연결하지 않았다. stdout은 기존 배포 수집 경로를 사용한다. AsyncAppender도 전송 부하·손실을 검증하는 슬라이스에서 추가한다.

## 검증

`./gradlew :support:logging:test :support:logging:ktlintCheck`로 실제 Spring Boot 로깅 초기화, 프로파일 조합과 전송 차단, 출력 정제를 검증한다. API·worker·batch 조립에 대한 회귀는 루트의 `./gradlew test ktlintCheck`로 확인한다.
