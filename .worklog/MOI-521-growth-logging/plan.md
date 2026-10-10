# MOI-521 계획

체크는 사람이 승인한 단계에만 한다.

- [x] 1. 컨텍스트 수집과 추천안 작성 (`context.md`, `recommendation.md`, `tbd.md`)
- [x] 2. 팀 결정: 지표·도구·식별 방식 — D1~D17(`decisions.md`). 남은 것(처리방침 법률 검토·전체 내용)은 `tbd.md`, live 전까지
- [x] 3. Wiki 반영 (`wiki-sync.md`)
- [x] 4. 로깅 계약 확장 (PR 1 #198, `support:logging`). 머지(2026-10-10)
- [ ] 5. 사건 기록 준비 (PR 2, `core-api`): `analyticsId` 생성기·내 정보 응답, 국외 이전 필수 약관과 기존 회원 일괄 동의. 아래 5단계 상세. 체크포인트 A 승인(2026-10-10, 초안 약관 내용 포함). PRD R180·R181·SSOT 갱신 완료. 체크포인트 B 승인(2026-10-10). 구현·리뷰 반영·전체 검증 완료. 체크포인트 C 승인(2026-10-10). ship-pr 진행
- [ ] 6. 사건 16개 기록 (PR 3, `core-api`): 아래 6단계 상세. 테스트 프로필은 그로스 INFO를 거르므로 writer를 주입해 검증(PR 1 QA 참고 4). 숫자 속성에 개인 식별 값 금지를 호출 지점 리뷰로 확인
- [ ] 7. 전송 (PR 4, infra): `ANALYTICS_ID_HMAC_KEY` 주입, 라우터 v3(그로스 필드 허용 + PostHog HTTP 출력), dev 전송 켜고 끄는 변수. Terraform plan까지. growth 줄은 예약 필드 외 MDC 키가 붙을 수 있으니 v3 허용 목록에서 거른다(4단계 리뷰 참고 4). `analyticsId`가 없는 줄의 PostHog `distinct_id` 처리와, 속성이 PostHog 예약 이름(`token`·`distinct_id` 등)을 덮지 않게 변환(PR 1 QA 참고 5)
- [ ] 8. 탈퇴 회원 PostHog 삭제 배치 (PR 5, D8)
- [ ] 9. dev에서 DB 대조와 퍼널 확인

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

PR 2 머지 후 이 절을 구체화해 다시 승인받는다. 현재 방향:

- Manager가 커밋 단위 안에서 `GrowthEvent`(사건 코드 enum, 주체 회원 ID, 속성)를 Spring 이벤트로 발행하고,
  `GrowthEventListener`(`@TransactionalEventListener(AFTER_COMMIT)`)가 `analyticsId`를 계산해 `GrowthEventWriter`로 기록한다. outbox는 쓰지 않는다(recommendation 3.1).
- 사건의 주체(analyticsId 대상): 신청 수락·반려는 신청자, 룸 확정·완료·취소·재개는 방장, 이탈은 나간 회원.
- 제외: 탈퇴 시 대기 신청 일괄 철회(건별 정보 없음), 방장 이탈 시 대기 신청자 승격(수락 메서드를 거치지 않음), 슬롯 초과 종료.
- `ReviewSkipRecorder`는 트랜잭션이 없어 AFTER_COMMIT이 돌지 않으므로 `@Transactional`을 붙인다.
- 중복·멱등 분기(룸 생성 중복, 완료 재전송, 탈퇴·복구 멱등)에서는 기록하지 않는다.

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
