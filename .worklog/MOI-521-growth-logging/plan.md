# MOI-521 계획

체크는 사람이 승인한 단계에만 한다.

- [x] 1. 컨텍스트 수집과 추천안 작성 (`context.md`, `recommendation.md`, `tbd.md`)
- [x] 2. 팀 결정: 지표·도구·식별 방식 — D1~D17(`decisions.md`). 남은 것(처리방침 법률 검토·전체 내용)은 `tbd.md`, live 전까지
- [x] 3. Wiki 반영 (`wiki-sync.md`)
- [ ] 4. 로깅 계약 확장 (PR 1, `support:logging`) — 아래 상세. 체크포인트 A·B 승인(2026-10-10). 구현·리뷰 반영·전체 검증 완료. 체크포인트 C 승인(2026-10-10). ship-pr 진행
- [ ] 5. 1차 사건 기록 (PR 2, `core-api`): 사건 16개, `analyticsId`, 국외 이전 필수 약관·기존 회원 일괄 동의 마이그레이션. 테스트 프로필은 그로스 INFO를 거르므로 writer를 주입해 검증(PR 1 QA 참고 4). 숫자 속성에 개인 식별 값 금지를 호출 지점 리뷰로 확인
- [ ] 6. 전송 (PR 3, infra): 라우터 v3(그로스 필드 허용 + PostHog HTTP 출력), dev 전송 켜고 끄는 변수. Terraform plan까지. growth 줄은 예약 필드 외 MDC 키가 붙을 수 있으니 v3 허용 목록에서 거른다(4단계 리뷰 참고 4). `analyticsId`가 없는 줄의 PostHog `distinct_id` 처리와, 속성이 PostHog 예약 이름(`token`·`distinct_id` 등)을 덮지 않게 변환(PR 1 QA 참고 5)
- [ ] 7. 탈퇴 회원 PostHog 삭제 배치 (PR 4, D8)
- [ ] 8. dev에서 DB 대조와 퍼널 확인

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
