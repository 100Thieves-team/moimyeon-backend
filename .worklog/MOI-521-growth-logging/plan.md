# MOI-521 계획

체크는 사람이 승인한 단계에만 한다.

- [x] 1. 컨텍스트 수집과 추천안 작성 (`context.md`, `recommendation.md`, `tbd.md`)
- [x] 2. 팀 결정: 지표·도구·식별 방식 — D1~D17(`decisions.md`). 남은 것(처리방침 법률 검토·전체 내용)은 `tbd.md`, live 전까지
- [x] 3. Wiki 반영 (`wiki-sync.md`)
- [x] 4. 로깅 계약 확장 (PR 1 #198, `support:logging`). 머지(2026-10-10)
- [x] 5. 사건 기록 준비 (PR 2 #200, `core-api`) 머지(2026-10-11): `analyticsId` 생성기·내 정보 응답, 처리방침 v1.2(PostHog 국외 이전 고지, D19로 별도 약관에서 변경). 아래 5단계 상세. 체크포인트 A 승인(2026-10-10, 초안 약관 내용 포함). PRD R180·R181·SSOT 갱신 완료. 체크포인트 B 승인(2026-10-10). 구현·리뷰 반영·전체 검증 완료. 체크포인트 C 승인(2026-10-10). ship-pr 진행
- [x] 6. 사건 16개 기록 (PR 3 #202, `core-api`) 머지(2026-10-11): 아래 6단계 상세. 체크포인트 A 승인(2026-10-11). 체크포인트 B 승인(2026-10-11). 구현·컨벤션 리뷰 반영·전체 검증 완료. 체크포인트 C 승인(2026-10-11). ship-pr 진행. 테스트 프로필은 그로스 INFO를 거르므로 writer를 주입해 검증(PR 1 QA 참고 4). 숫자 속성에 개인 식별 값 금지를 호출 지점 리뷰로 확인
- [ ] 7. 전송 (PR 4, infra): 계획 승인(2026-10-12, live는 S3 growth 필드 저장·전송 끔, dev 전송 켬)(아래 7단계 상세). 구현·로컬 검증 완료(2026-10-12): fmt·validate·모듈 test 2개·smoke·계약 스크립트 15개·deploy 입력 test. CI plan 판독 전. `ANALYTICS_ID_HMAC_KEY` 주입, 라우터 v3(그로스 필드 허용 + PostHog HTTP 출력), dev 전송 켜고 끄는 변수. Terraform plan까지. growth 줄은 예약 필드 외 MDC 키가 붙을 수 있으니 v3 허용 목록에서 거른다(4단계 리뷰 참고 4). `analyticsId`가 없는 줄의 PostHog `distinct_id` 처리와, 속성이 PostHog 예약 이름(`token`·`distinct_id` 등)을 덮지 않게 변환(PR 1 QA 참고 5)
- [ ] 8. 탈퇴 회원 PostHog 삭제 배치 (PR 5, D8)
- [ ] 9. dev에서 DB 대조와 퍼널 확인

## 7단계 상세: 전송 (PR 4, infra)

영향 환경: dev(전송 켬), live(키·전송 끔, D10). shared는 CI plan 역할의 SSM 읽기 거부 목록만.

### A. `analyticsId` 키 주입 (`moimyeon-environment`)

- `secrets.tf`: `/moimyeon/{env}/core-api/ANALYTICS_ID_HMAC_KEY`(D14, 사전 생성) ARN을 core-api 컨테이너 `secrets`(`ANALYTICS_ID_HMAC_KEY`)와
  `ssm_parameter_arns`(실행 역할 ssm:GetParameters)에 조건부로 추가한다. 조건은 새 변수 `growth_analytics_enabled`(env tfvars, dev true·live false).
  파라미터가 없는 live에 주입하면 태스크가 뜨지 않으므로 반드시 변수 뒤에 둔다.
- `shared-foundation/terraform_ci.tf` 앱 SecureString 읽기 거부 목록에 새 ARN 추가.
- tfvars 키 allowlist 계약(`terraform-config-contract.sh`)과 README 사전 생성 목록에 반영.

### B. 라우터 v3 (`application-logging/router/v3`, v1·v2는 그대로)

- `sanitize.lua`: `category=growth`·INFO 레코드에서만 `eventId`(UUID)·`analyticsId`(32 hex)·`properties`를 복사한다.
  properties는 키 `[A-Za-z][A-Za-z0-9_]*` 16개 이하, 값은 숫자·Boolean·128자 이하 문자열만(앱이 UUID·enum을 문자열로 낸다).
  그 밖의 필드(예약 외 MDC 키 포함)는 지금처럼 버린다.
- PostHog 경로(전송을 켠 환경만 템플릿에 포함):
  1. growth 레코드를 `rewrite_tag`로 `posthog.*`에 복제(S3 growth 원본은 유지).
  2. Lua로 PostHog 이벤트 모양으로 바꾼다: `{api_key, event=eventCode, distinct_id=analyticsId, uuid=eventId, timestamp,
     properties+{environment, service, release}}`. `analyticsId`가 없으면 PostHog로 보내지 않는다(S3에는 남음).
     properties의 `distinct_id`·`token`·`api_key` 같은 이름은 버린다.
  3. `http` 출력: `us.i.posthog.com:443/i/v0/e/`, `format json`(배열), TLS, gzip, Retry_Limit 3.
- 프로젝트 토큰은 `/moimyeon/{env}/log-router/POSTHOG_PROJECT_TOKEN`(D14) ARN을 라우터 컨테이너 `secrets`로 주입하고
  Lua가 `os.getenv`로 읽는다. 실행 역할 GetParameters에 그 ARN을 추가한다(전송을 켠 환경만).
- 모듈 변수: `posthog = { enabled, host, project_token_parameter_arn }`. 템플릿에 host·port·tls 자리를 두어 smoke에서 가짜 서버로 바꾼다.
- `revisions = ["v1","v2","v3"]`, `active_revision = "v3"`.

### C. 검증

- `terraform fmt -check`, 모듈 `terraform test`(tftest: v3 설정 객체, 라우터 secrets 유무, live에서 PostHog 경로 없음).
- `logging_smoke.py`: 실제 Fluent Bit 이미지로 growth 필드 전달, PostHog 가짜 서버 수신(배열·api_key·distinct_id·uuid·environment),
  analyticsId 없는 레코드 미전송, 금지 속성 이름 제거.
- 계약 테스트(`terraform-config-contract.sh`, `terraform-ci-contract.sh` 등) 통과.
- PR CI plan 판독(체크포인트): 예상 변경은 설정 객체 v3 추가, 실행 역할 정책 확대(SSM 2개), 태스크 정의 틀 교체. replacement·데이터 저장소 변경 없음.

### 영향

- dev: 다음 배포부터 `analyticsId` 생성, growth 필드가 S3에 남고 PostHog로 전송된다.
- live: 설정 객체 v3로 바뀌지만 PostHog 경로·키 주입은 꺼져 있다. growth 필드는 S3 growth에 남는다(처리방침 v1.2 고지 범위, 해외 전송 없음).

## 5단계 상세: 사건 기록 준비 (PR 2)

### 접근

사건 기록(PR 3) 전에 필요한 두 가지를 먼저 넣는다. 하나는 회원별 `analyticsId`, 다른 하나는 국외 이전 동의다.

**analyticsId**

- `AnalyticsIdGenerator`(Generator, 트랜잭션 없음, `domain/analytics`): `HMAC-SHA-256(키, memberId 16바이트)` 앞 16바이트를 소문자 hex 32자로.
- 키는 `moimyeon.analytics.id-hmac-key`(`ANALYTICS_ID_HMAC_KEY`) `@ConfigurationProperties`. **값이 없으면 기동은 하고 `analyticsId`를 만들지 않는다**(null).
  키 주입(Terraform)이 PR 4라서, 그 전에 배포돼도 dev가 기동 실패하지 않게 하기 위해서다. 값이 없으면 시작 시 WARN 한 줄.
- 내 정보 `GET /v1/members/me` 응답에 `analyticsId`(nullable) 추가. 경로: `MemberFacade → MemberService → AnalyticsIdGenerator`.
  FE가 이 값으로 PostHog `identify`를 호출한다(D6). RestDocs 필드 추가, OpenAPI 재생성 확인.

**국외 이전 필수 약관 (D13·D16)**

- `TermsType`에 `OVERSEAS_TRANSFER` 추가.
- `V36` 마이그레이션: `OVERSEAS_TRANSFER v1.0`(필수, ACTIVE) 한 행 + 기존 회원 전원(탈퇴 포함 행 전체) 동의 기록. `seed.sql`에도 같은 약관 행.
- 내용은 `privacy-policy-draft.md`의 국외 이전 문구다. **법률 검토 전 초안**이라 live 정식 출시 전에 검토 결과로 새 버전을 낸다(tbd 2).
- `TermsPublication`이 모든 `TermsType`에 활성 약관을 요구하므로 enum과 행이 함께 들어가야 한다. 약관 개수를 세는 기존 테스트 4곳을 함께 고친다.
- 배포 중 이전 서버가 새 enum 값을 읽어 실패할 수 있으나, 출시 전 dev라 설계 근거로 삼지 않는다(메모리: dev 배포 유실 무시).

### 변경 지점

| 영역 | 파일 |
| --- | --- |
| analyticsId | `domain/analytics/AnalyticsIdGenerator.kt`·`AnalyticsProperties.kt`(신규), `application.yml`. 테스트 프로필은 키 없이 기동(I8) |
| 내 정보 | `MemberService`(조회 메서드 추가), `MemberFacade`, `MemberMeResponse`, `MemberControllerTest`(RestDocs) |
| 약관 | `core-enum/TermsType.kt`, `db/migration/V36__...sql`, `seed.sql`, `TermsHttpContextTest`·`TermsServiceIT`·`ProfileServiceIT` 등 |
| 문서 | `support/logging` 아님. `docs/` 영향 없음. Dockerfile AOT 블록은 키가 선택값이라 수정 불필요 |

### 만들 테스트

1. `AnalyticsIdGenerator`: 같은 회원은 같은 값, 다른 회원·다른 키는 다른 값, 32자 소문자 hex(`GrowthEventEntry` 검증 통과), 키가 없으면 null
2. `AnalyticsIdGenerator`: 알려진 키·회원 ID에 대해 고정 기대값(다른 언어 구현과 맞출 기준값)
3. 내 정보: 키가 있으면 `analyticsId`가 응답에 있고, 없으면 null (RestDocs 필드 포함)
4. 약관: 활성 약관 목록에 `OVERSEAS_TRANSFER`가 포함되고 필수다
5. 가입: 신규 회원은 `OVERSEAS_TRANSFER`까지 필수 약관 전부에 동의 기록이 생긴다
6. 마이그레이션(MySQL IT, `MySqlSchemaValidationIT` 계열): V36 후 기존 회원 전원이 `OVERSEAS_TRANSFER`에 동의 기록을 갖는다

### 영향

- API: `GET /v1/members/me`에 nullable 필드 하나 추가(하위 호환). FE는 PostHog `identify`에 사용.
- DB: 약관 1행·동의 기록 N행 추가. 스키마 변경 없음(`type VARCHAR(20)`에 17자).
- 가입 흐름: 필수 약관이 3개가 된다. 가입 시 자동 동의는 기존 `agreeRequired`가 처리한다.

## 6단계 상세: 사건 16개 기록 (PR 3)

### 구조

```text
Manager(@Transactional) ── growthEventPublisher.publish(GrowthEvent) ──▶ Spring ApplicationEvent
                                                                          │ 커밋 후(AFTER_COMMIT, 같은 스레드)
                                                                          ▼
                                       GrowthEventListener ── analyticsIdGenerator.generate(memberId)
                                                           └─ GrowthEventWriter.write(GrowthEventEntry)
```

- `core/event/growth` 패키지(outbox 옆): `GrowthEvent`(사건 코드 enum, 주체 회원 ID, 속성), `GrowthEventCode`(16개, `대상.과거형동사` 값),
  `GrowthEventPublisher`(`ApplicationEventPublisher` 감싸기), `GrowthEventListener`.
- 롤백되면 기록하지 않는다(AFTER_COMMIT). 커밋 직후 프로세스가 죽으면 빠질 수 있다(D-21, DB와 대조).
- 리스너는 예외를 삼키고 WARN 한 줄(`growth-event.listener.failed code=...`)을 남긴다. 커밋된 업무 요청을 로그 실패로 실패시키지 않는다.
- outbox는 쓰지 않는다(recommendation 3.1). `eventId`는 리스너에서 새 UUID.
- (변경, I18) `ReviewSkipRecorder`에는 `@Transactional`을 붙이지 않고, 리스너가 트랜잭션 밖 발행을 바로 기록한다(`fallbackExecution`).

### 사건표

주체는 `analyticsId`를 계산할 회원이다(그 사람의 퍼널에 남는다). 속성 값은 ID·숫자·enum·Boolean만(PR 1 계약).

| # | 사건 | 기록 지점 | 주체 | 속성 |
| --- | --- | --- | --- | --- |
| 1 | `member.signed_up` | `MemberRegistrar.register` | 신규 회원 | provider |
| 2 | `member.withdrew` | `MemberWithdrawer.withdraw` 실제 삭제 분기 | 탈퇴 회원 | — (탈퇴로 나간 룸은 `room_participant.left` byWithdrawal로 센다, 리뷰 반영) |
| 3 | `member.restored` | `MemberRestorer.restore` 실제 복구 분기 | 복구 회원 | — |
| 4 | `resume.registered` | `ResumeRegistrar` | 소유 회원 | resumeId, activeResumeCount(I19) |
| 5 | `room.created` | `RoomManager.create` 중복 반환 이후 | 방장 | roomId, jobPostingId, jobRoleId, interviewStage, minParticipants, maxParticipants, daysUntilStart |
| 6 | `room_application.submitted` | `RoomApplicationSubmissionManager` 제출 | 신청자 | roomId, applicationId |
| 7 | `room_application.withdrawn` | 같은 파일 철회 | 신청자 | roomId, applicationId |
| 8 | `room_application.accepted` | `RoomApplicationManager` 수락(슬롯 초과 종료 제외) | 신청자 | roomId, applicationId, minutesSinceSubmitted |
| 9 | `room_application.rejected` | 같은 파일 반려 | 신청자 | roomId, applicationId, rejectReason(있을 때, I20) |
| 10 | `room.confirmed` | `RoomManager` 확정 | 방장 | roomId, participantCount, hoursSinceCreated, reconfirmed(리뷰 반영) |
| 11 | `room.completed` | `RoomProgressManager` 수동·자동 공통 완료(재전송 제외) | 방장 | roomId, completedBy(HOST/SYSTEM), confirmedCount, attendedCount |
| 12 | `room.canceled` | `RoomManager.cancelWithoutGuard` | 방장 | roomId |
| 13 | `room.recruiting_reopened` | `RoomLeaveManager` 재개 공통 지점(참여자 이탈·방장 위임 둘 다) | 방장(재개 시점) | roomId, reason(PARTICIPANT_LEFT/HOST_LEFT) |
| 14 | `room_participant.left` | `RoomLeaveManager` 실제 나간 경우 | 나간 회원 | roomId, role, roomStatus, byWithdrawal |
| 15 | `review.submitted` | `ReviewSubmissionManager` | 작성자 | roomId, reviewId, anonymous(I20) |
| 16 | `review.skipped` | `ReviewSkipRecorder` 신규 기록 분기 | 작성자 | roomId |

기록하지 않는 경우: 룸 생성 중복 반환, 완료 재전송, 탈퇴·복구 멱등 반환, 슬롯 초과 종료, 후기 건너뛰기 중복,
탈퇴 시 대기 신청 일괄 철회(건별 정보 없음, `member.withdrew`로 대신), 방장 이탈 시 대기 신청자 승격(수락 메서드를 거치지 않음).
탈퇴 흐름은 단계별 커밋이라 `room_participant.left`·`room.recruiting_reopened`가 `member.withdrew`보다 먼저, 회원 삭제가 실패해도 남는다.

### 만들 테스트

1. `GrowthEventListener`(단위): 사건을 `GrowthEventEntry`로 바꿔 쓴다(코드·analyticsId·속성), 키가 없으면 analyticsId 없이 쓴다, writer 예외를 삼킨다
2. 커밋·롤백(ContextTest IT, 바깥 트랜잭션 없음): 커밋되면 한 번 기록, 롤백되면 기록 없음, 트랜잭션 밖이면 바로 기록(실제 writer logger 캡처, I22)
3. Manager별(단위, MockK): 16개 사건마다 기록 지점에서 맞는 코드·주체·속성으로 발행, 위 "기록하지 않는 경우"마다 발행 안 함
4. `ReviewSkipRecorder`: 트랜잭션 밖 발행이 바로 기록된다(IT 2의 세 번째 테스트)

### 영향

- API 변화 없음. 로그 출력만 늘어난다(라우터 v2는 그로스 필드를 버리므로 PR 4 전까지 S3에는 사건명만).
- 생성자 변경으로 Manager 단위 테스트 다수의 조립부가 바뀐다(relaxed mock 추가). 예상 파일 수 35개 안팎(상한 50).

## 4단계 상세: 로깅 계약 확장

### 접근

`RequestLogEntry`·`RequestLogWriter` 선례를 따른다. 타입이 있는 엔트리를 payload로 넘기고, `LogSanitizer`가
그 타입을 알아볼 때만 예약 필드(`eventCode`·`category` 등)를 채운다. 일반 `log.info`로는 그로스 사건을 만들 수 없다.

출력 한 줄(배포 JSON) 예:

```json
{"schemaVersion":1,"timestamp":"...","service":"core-api","environment":"dev","release":"...","level":"INFO",
 "logger":"...","thread":"...","eventCode":"room.created","message":"room.created","category":"growth",
 "eventId":"<uuid>","analyticsId":"<32 hex>","properties":{"roomId":"<uuid>","minParticipants":3}}
```

- `environment`는 기존 공통 필드라 D17(dev 걸러내기)에 그대로 쓴다.
- 사건 시각은 기존 `timestamp`(로그 기록 시각)를 쓴다. 커밋 직후 기록하므로 별도 `occurredAt`을 두지 않는다.

### 변경 지점 (`support/logging`)

| 파일 | 변경 |
| --- | --- |
| `GrowthEventEntry.kt` (신규) | `eventCode`, `eventId: UUID`, `analyticsId: String?`, `properties: Map<String, Any>`. 생성 시 검증 |
| `GrowthEventWriter.kt` (신규) | 항상 INFO. 표식 message와 payload로 엔트리를 넘긴다 |
| `LogSanitizer.kt` | 그로스 엔트리 분기: `eventCode`=엔트리 값, `category`="growth", `eventId`·`analyticsId`·`properties`. 예약 필드에 `eventId`·`analyticsId`·`properties` 추가 |
| `LoggingAutoConfiguration.kt` | `GrowthEventWriter` 빈 등록(`RequestLogWriter`와 같은 방식) |
| `README.md` | 그로스 사건 출력 계약 한 절 |

검증 규칙(자유 입력이 섞이지 않게):

- `eventCode`: `대상.과거형동사` 소문자 스네이크(예 `room_application.accepted`), 64자 이하.
- `analyticsId`: 32자리 소문자 16진수 또는 null.
- `properties`: 키는 기존 필드명 문법, 최대 16개. 값은 숫자·Boolean·UUID·enum형 문자열(`[A-Za-z0-9_.:-]`, 128자 이하)만. 공백이 든 문자열은 거부.

### 만들 테스트 (`support/logging/src/test`, 단위)

Service가 없는 공통 모듈이라 service 테스트 대신 `RequestLoggingTest`와 같은 단위 테스트로 스펙을 고정한다.

1. `GrowthEventEntry`: 올바른 값은 만들어진다 / 잘못된 eventCode·analyticsId·속성 키·공백 포함 값·속성 17개는 거부된다
2. `GrowthEventWriter`: INFO로 한 번 기록하고 payload에 엔트리를 담는다
3. `LogSanitizer`: 그로스 엔트리면 `eventCode`·`category=growth`·`eventId`·`analyticsId`·`properties`가 나온다
4. `LogSanitizer`: MDC·key-value로 `category`·`eventId`·`analyticsId`·`properties`를 주입해도 무시된다
5. `LogSanitizer`: 표식 메시지 없이 그로스 payload만 넣은 일반 로그는 그로스로 처리되지 않는다
6. 텍스트 formatter(local)에서도 한 줄로 출력된다

### 영향

- API 문서(RestDocs·OpenAPI): 없음.
- 외부 소비자: Fluent Bit 라우터. 활성 v2는 허용 목록 밖 필드를 버리므로 이 PR만으로는 S3 growth에 `eventCode`·`message`만 남는다.
  그로스 사건을 쓰는 코드가 아직 없어(5단계) 실제 출력은 없다. 필드 전달은 6단계 라우터 v3에서 연다.
- 기존 로그 출력: 바뀌지 않는다. 새 예약 필드 3개를 MDC·key-value 이름으로 쓰는 곳이 없는지 구현 때 grep으로 확인한다.
