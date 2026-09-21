# MOI-525 결정 기록

## D-01. 출력 allowlist를 일반 로그 계약으로 전환한다

2026-09-21 사용자 결정. MOI-411 DR-26의 "미등록 메시지는 원문을 숨기고 application.log로 표시"를 폐기하고, 메시지·인자·MDC·예외
메시지를 보존하는 일반적인 방식으로 바꾼다. 상세와 남은 일은 [MOI-411 DR-32](../MOI-411-logging-config/decisions.md#dr-32-출력-allowlist를-일반-로그-계약으로-되돌린다-dr-26-일부-폐기)에
기록했다.

- 범위: 앱 formatter 완화 + 로그 정리를 이 PR에서. 인프라 Lua 라우터는 별도 infra-change PR.
- 마스킹: `Bearer` 헤더값·JWT 형태만. 이메일·UUID·숫자 ID는 가리지 않는다.
- dev만 DEBUG. staging·live는 INFO 유지.

이유: 이번 이슈의 목적인 흐름 추적이 기존 계약에서는 성립하지 않았다. `log.debug { "room.create" }`와 `log.debug { "room.cancel" }`이
stdout에서 같은 `application.log`로 출력돼 구분되지 않았고, 식별자를 붙일 길도 없었다. 정적 메시지까지 숨기는 것은 개인정보 방어의
필수 조건이 아니었다.

## D-02. 메시지 형식은 고정 접두어 + key=value

`room.create memberId=... roomId=...`처럼 쓴다. 접두어는 grep 기준이고 key=value는 CloudWatch Logs Insights `parse`로 추출할 수 있다.
kotlin-logging `payload`로 구조화 필드를 넣는 방식도 formatter가 지원하지만, 이번 범위에서는 문자열 형식으로 통일해 호출 지점을 단순하게 둔다.

## D-03. 브랜치명·worklog 디렉터리를 ASCII로 정정

Linear가 제안한 한글 브랜치명을 `feat/MOI-525-trace-logs`로 바꿨다. PR이 열리기 전이라 rename이 안전하다. worklog 디렉터리도 같은 이름으로 옮겼다.

## D-04. Implement 레이어는 공개 메서드 전부에 진입 로그를 둔다

2026-09-21 사용자 결정("implement layer도 전부 추가해줘"). 분기·거절 지점만 고르는 대신 `@Component` Implement 클래스의 공개 메서드
전부에 DEBUG 진입 로그를 넣는다. 조회 Finder·Reader도 포함한다. dev에서 로그량이 늘지만 DEBUG는 staging·live에서 꺼져 있고,
한 요청 안의 호출 순서가 전부 보이는 쪽을 택했다.

- 접두어: `개념.역할.메서드`. 클래스명을 CamelCase 단어로 나눠 마지막 단어를 역할로, 앞 단어들을 `-`로 이어 개념으로 쓴다.
  예: `RoomApplicationSubmissionFinder.getPendingByApplicant` → `room-application-submission.finder.getPendingByApplicant`.
- 값: UUID·Long·Int·Boolean·enum 파라미터와 컬렉션 크기(`xxxCount`)만. String 파라미터는 이름이 `Id`로 끝나도 외부 계정 식별자
  (`providerId`)는 제외. 이메일·닉네임·content·rawCredential·url·DTO는 제외.
- 삽입은 스크립트로 일괄 처리했고(표현식 본문 5개는 블록 본문으로 변환), Command 객체만 받는 Manager 7개는 손으로 식별자를 보강했다.

## D-05. QA 리뷰 반영: 앱 밖 예외 메시지는 남기지 않는다

2026-09-21 QA 리뷰(CONDITIONAL, 필수 1·권고 5) 결과를 사용자 결정으로 이 PR에서 반영했다.

- 필수: `ApiControllerAdvice`·`AsyncExceptionHandler`가 예전부터 `e.message`를 로그에 넣고 있었고, 출력 계약 완화로 거부된 입력값·
  `Duplicate entry '<닉네임>'` 같은 값이 노출될 수 있었다. 어드바이스는 `CoreException`·`CoreApiException`만 메시지를 남기고 프레임워크
  예외는 파라미터 이름·타입만 남기도록 바꿨다. `LogSanitizer`는 `io.plady.*` 타입의 예외 메시지만 보존한다. "일반적인 방식"에서 한 발
  물러난 지점이며, 앱 예외 메시지는 ErrorType의 정적 문구라 손실이 없다.
- 권고: 라우터가 읽는 필드를 예약해 MDC·key-value 주입을 막고, `traceId`·`spanId` 형식 검증을 되살렸다. `NotificationMessageWorker`·
  `PendingOutboxRelayScheduler`의 매 tick DEBUG를 없애고 건별·건수 로그로 바꿨다. `JsoupOpenGraphClient`는 실패·리다이렉트 한도를
  DEBUG로 내리고 SSRF 차단만 host로 INFO를 남긴다. 오버로드 접두어는 `validate.byRoom`/`validate.byStatus`로 구분했고,
  `ResumeRegistrar.register`는 2-인자 버전이 3-인자 버전에 위임하므로 위임 쪽 로그를 없애 중복을 제거했다.

## D-06. 리뷰봇 2차 반영: 예외 메시지 보존은 타입 마커로, 제어 문자는 이스케이프

2026-09-21 PR #131 멀티에이전트 리뷰 2차(필수 1·제안 1·후속 1)를 모두 반영했다.

- 필수: 텍스트 formatter가 개행·제어 문자를 그대로 이어붙여 로그 위조가 가능했다. 모든 문자열 값의 `\n`·`\r`·`\t`는 이스케이프하고 나머지
  제어 문자는 제거한다. JSON 경로도 같은 값을 쓰므로 원문에 개행이 있던 값은 두 형식 모두 `\n` 두 글자로 남는다.
- 후속으로 분류된 항목이지만 지금 반영: 예외 메시지 보존 판정을 `io.plady.` 패키지 접두어에서 `SafeLogMessage` 마커 인터페이스로 바꿨다.
  `CoreException`·`CoreApiException`·`ResumeSummaryGenerationException`·`ResumeFileStorageException`이 구현한다. worker의
  `NotificationProcessingException` 계열은 메시지에 식별자를 보간하므로 마커를 붙이지 않았고, 로그에는 타입·스택만 남는다.
- 제안: 어드바이스·비동기 핸들러의 LogLevel 3분기 중복을 `KLogger.at(level, cause, message)` 확장 하나로 모았다.

## D-07. 리뷰봇 3차 반영: 필드 키 이름은 문법으로 제한

2026-09-21 PR #131 3차 리뷰(CodeRabbit Major 1·제안 2). 값 이스케이프만 있고 키 이름 검증이 없어 개행이 든 MDC 키로 텍스트 로그를
위조할 수 있었다. 키는 영문자로 시작하는 `[A-Za-z0-9_.-]` 64자 이하만 받고 나머지는 버린다. `errors.md`에 `exception.async.*` 접두어를
추가했고, 기본 eventCode 리터럴은 `LogSanitizer` 상수로 공유한다. 리뷰 대응 상한(2회)을 넘긴 반영이며 사용자 승인으로 진행했다.

