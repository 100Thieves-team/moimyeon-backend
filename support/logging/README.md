# 공통 로깅 설정

전체 문제·선택 이유·실제 사용 흐름은 [로깅 기반 작업 보고서](../../docs/architecture/logging-report.md)에 정리했다.

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

허용된 사건은 `service.ready`와 타입이 있는 요청 요약이다. `RequestLogWriter`에 `RequestLogEntry`를 전달하면 `http.request.completed` 또는 `http.request.slow`로 기록하고 method·route·status·durationMs·errorCode·서버 발급 requestId를 보존한다. 요청 사건명만 붙인 임의 Map은 통과하지 않는다. 나머지 자유 형식 로그는 `application.log` 또는 `application.error`로 표시하며 유효한 MDC requestId로 요청 요약과 연결한다.

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

`RequestLogEntry.routeTemplate`에는 서버가 등록한 MVC 매핑 패턴을 전달해야 한다. 생성자는 문법·길이·상태·시간·오류 코드를 검증할 뿐, 원문 URI를 안전한 템플릿으로 변환하지 않는다. 예를 들어 숫자 경로 조각이 개인정보인지는 문자열 검사로 판별할 수 없다. core-api의 인터셉터가 실제 매핑 패턴을 전달하며 매핑을 얻지 못하면 `UNMATCHED`를 사용한다. 확장 HTTP 메서드는 `UNKNOWN`으로 정규화한다.

기록 예시는 다음과 같다. body·쿠키·임의 헤더를 받을 필드는 없다.

```kotlin
writer.write(RequestLogEntry("POST", "/v1/rooms/{roomId}", 201, 42))
```

Sentry는 기존 `SentryPrivacyFilter`를 계속 사용한다. JSON encoder의 정제가 Sentry에 자동 적용되는 것은 아니다. 이번 변경은 Sentry의 새 이벤트 필드나 fingerprint를 추가하지 않는다.

core-api에는 실제 요청 수집을 연결했다. 공통 모듈에는 Servlet 의존이 없으며 HTTP 필터·리스너·인터셉터는 core-api에 둔다. worker와 batch에는 HTTP 필터를 추가하지 않았다.

S3·FireLens·보존 정책은 [별도 인프라 모듈](../../infra/terraform/modules/application-logging/README.md)에 작성했고 실제 AWS 적용은 아직이다. 적용 전 실행 환경은 기존 수집 경로를 사용한다. 알림·일반 비동기 작업 context 전파는 후속 범위이며 Logback AsyncAppender는 추가하지 않았다.

## HTTP 완료 기록

필터는 Boot의 HTTP observation 다음, Security 이전에 동작한다. 서버가 발급한 requestId를 request attribute와 MDC에 두고 외부 X-Request-Id는 사용하지 않는다. 응답에 새 헤더를 추가하지도 않는다. 필터와 완료 리스너는 자기 작업 뒤 기존 MDC를 복원한다.

ServletRequestListener의 requestDestroyed에서 최종 상태와 단조 시계의 경과 시간을 기록한다. 필터 finally에서 비동기 요청을 미리 200으로 기록하지 않는다. 오류 페이지·다른 핸들러로의 async dispatch가 있더라도 최초 MVC 경로를 보존하고, 원자적 완료 표시로 중복을 막는다. 이 기록은 Servlet 처리 결과다. 네트워크의 마지막 flush나 클라이언트 수신 성공을 보장하지 않는다.

MVC 진입 전에 끝난 요청은 등록된 템플릿을 얻지 못할 수 있다. OAuth 시작·콜백과 health는 고정 분류를 사용하고, 다른 Security 조기 종료는 UNMATCHED로 남긴다. UNMATCHED는 매핑을 관측하지 못했다는 뜻이며 HTTP 404와 동의어가 아니다. body·query·토큰·원문 URI를 읽어 보충하지 않는다.

ApiResponse의 오류 코드는 ResponseBodyAdvice가 객체에서 읽고, 인증 오류는 기존 AuthErrorWriter 어댑터가 메타데이터를 전달한다. response wrapper는 본문을 캐싱·파싱·수정하지 않는다. 기록 실패가 응답을 바꾸지 않도록 완료 리스너에서 처리한다.

완료 로그는 처음 캡처한 HTTP trace/span을 사용한다. Security나 다른 계층의 자식 span은 span ID가 달라도 같은 trace ID로 연결된다. 실제 sampling 0 설정의 내장 서버 테스트에서 핸들러 trace와 완료 JSON의 HTTP span을 확인한다. Callable·@Async 작업 내부의 MDC 전파까지 구현한 것은 아니다. Servlet async 완료·재디스패치의 기록과 작업 내부 context 전파를 구별한다.

## 검증

`./gradlew :support:logging:test :support:logging:ktlintCheck`로 실제 Spring Boot 로깅 초기화, 프로파일 조합과 전송 차단, 출력 정제를 검증한다. API·worker·batch 조립에 대한 회귀는 루트의 `./gradlew test ktlintCheck`로 확인한다.
