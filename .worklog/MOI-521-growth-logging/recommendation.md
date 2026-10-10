# MOI-521 그로스 지표·로깅 추천안

상태: **2026-10-10 팀 결정으로 추천안을 채택했다(`decisions.md` D1~D9). 남은 것은 `tbd.md`.** 2026-10-05 작성. 근거는 `context.md`의 Wiki PRD·회의록과 코드 위치.

## 0. 한 줄 요약

서버는 "DB에 확정된 핵심 사건" 16개만 남긴다. 기존 로그 경로(Fluent Bit)에서 S3와 **PostHog**로 함께 보내고,
화면 조회·클릭과 광고 유입(UTM)은 FE가 같은 PostHog 프로젝트에 남긴다. 두 쪽은 서버가 발급한 분석용 ID로 이어 붙인다.

## 1. 무엇을 보려는가 — 지표 추천

### 1.1 핵심 지표(북극성 지표) 후보

**주간 완료 모의면접 참여 인원** = 그 주에 `COMPLETED`된 룸의 출석자 수 합.

- 이 서비스가 사용자에게 주는 가치는 "모의면접을 실제로 했다"이다. 가입·신청·생성은 그 앞 단계일 뿐이다.
- 방장 쪽(룸이 만들어지는가)과 참여자 쪽(사람이 모이는가)이 둘 다 잘 돌아야 올라간다.
- 9/27 허들의 "확정·진행 완료·출석 처리 이후 트래킹 연동" 방향과 맞는다.
- Wiki에 서비스 단위 핵심 지표는 정의돼 있지 않다. 이건 제안이며 팀 확정이 필요하다.

### 1.2 퍼널 — 사용자 한 명이 가치에 닿기까지

| 단계 | 지표 | 근거 PRD | 기록 주체 |
| --- | --- | --- | --- |
| 유입 | 광고·채널별 방문 수 | 9/8 허들 "광고 전환율" | FE |
| 탐색 | 룸 상세 조회 후 신청 시작률 | 룸-탐색 핵심 지표 | FE |
| 가입 | 로그인 시작 후 가입 완료율 | 회원-및-프로필 핵심 지표 | FE(시작) + **BE(가입 완료)** |
| 첫 행동 | 가입 후 첫 신청 또는 생성 전환율 | 회원-및-프로필 | **BE** |
| 매칭 | 신청 수락률, 신청 → 수락 걸린 시간 | 룸-참여 핵심 지표 | **BE** |
| 확정 | 최소 인원 달성 룸의 진행 확정률, 생성 → 확정 시간 | 룸-진행-확정 핵심 지표 | **BE** |
| 완료 | 확정 룸 중 완료 비율, 출석률 | 룸-진행-마무리-및-출석 | **BE** |
| 후기 | 완료 룸의 후기 작성률, 건너뛰기 비율 | 유저-후기-및-신뢰-관리 핵심 지표 | **BE** |
| 재참여 | 첫 완료 후 30일 안에 두 번째 룸에 신청·생성한 비율 | (Wiki에 없음, 제안) | **BE** |

### 1.3 방장 쪽(공급) 지표

- 룸 생성 수(주간), 생성 후 첫 신청까지 걸린 시간
- 일정 전에 최소 인원을 못 채우고 끝난 룸 비율 — 룸-진행-확정 보조 지표
- 확정 후 이탈로 모집이 다시 열린 비율(`room.recruiting_reopened`)
- 방장 이탈·위임 비율

### 1.4 처음부터 하지 않는 것

- 질문 작성, 라운드 피드백, 클로징, 방명록, 알림 발송·클릭. 2차 후보로 둔다(7절).
  핵심 퍼널이 안정적으로 쌓이는 걸 먼저 확인한 뒤 늘린다.
- 정확한 건수 보고. 로그는 일부 유실·중복이 있을 수 있어(MOI-411 DR-21) 정확한 수치는 DB로 확인한다.
  로그의 역할은 **추세·전환율·유입 경로와 연결된 분석**이다.

## 2. 서버가 남길 사건(1차, 16개)

이름은 `대상.과거형동사`. 모두 **DB 커밋이 끝난 뒤**에 한 번 기록한다.

| 사건 | 기록 위치(커밋 지점) | 추가로 남길 값 | outbox 있음 |
| --- | --- | --- | --- |
| `member.signed_up` | `SocialAuthService.kt:24` 신규 등록 분기 | provider | 없음 |
| `member.withdrew` | `MemberWithdrawer.kt:30` | — | 없음 |
| `member.restored` | `MemberRestorer.kt:23` (실제 복구된 경우만) | — | 없음 |
| `resume.registered` | `ResumeRegistrar.kt:39` | 첫 이력서 여부 | 없음 |
| `room.created` | `RoomManager.kt:70` (중복 반환 제외) | companyId, jobRoleId, 최소 인원, 일정까지 남은 일수 | 없음 |
| `room_application.submitted` | `RoomApplicationSubmissionManager.kt:44` | roomId | 있음 |
| `room_application.withdrawn` | 같은 파일 `:80` | roomId | 없음 |
| `room_application.accepted` | `RoomApplicationManager.kt:47` | roomId, 신청 후 걸린 시간 | 있음 |
| `room_application.rejected` | 같은 파일 `:103` | roomId | 있음 |
| `room.confirmed` | `RoomManager.kt:201` | 참여 인원, 생성 후 걸린 시간 | 있음 |
| `room.completed` | `RoomProgressManager.kt:37,78` | 완료 주체(HOST/SYSTEM), 확정 인원, 출석 인원 | 있음 |
| `room.canceled` | `RoomManager.kt:171` | 사유 | 있음 |
| `room.recruiting_reopened` | `RoomLeaveManager.kt:171`, `:114`(위임 경로) | 사유(참여자 이탈/방장 이탈) | 일부 |
| `room_participant.left` | `RoomLeaveManager.kt:49` | roomId, 역할, 룸 상태 | 없음 |
| `review.submitted` / `review.skipped` | `ReviewSubmissionManager.kt:31` / `ReviewSkipRecorder.kt:17` | roomId | 없음 |

공통 필드: `eventId`(중복 제거용 UUID), `occurredAt`, `analyticsId`(4절), `requestId`, `release`.

**남기지 않는 값:** 이메일, 닉네임, 이력서·후기·질문 본문, 회사·공고 URL 원문, 반려 사유 자유 입력.
값은 ID·숫자·enum만 허용한다.

### 코드에서 미리 고쳐야 하는 점(조사에서 발견)

- 가입과 재로그인이 같은 `SocialAuthentication.LoggedIn`으로 반환돼 바깥에서는 구분이 안 된다(`SocialAuthentication.kt:8`).
- 룸 생성 중복 요청이 기존 룸을 그대로 돌려줘서(`RoomManager.kt:75`) 새로 만든 건지 구분이 안 된다.
- 확정 룸에서 방장 위임으로 모집이 다시 열릴 때 `ROOM_RECRUITING_REOPENED`가 발행되지 않는다(`RoomLeaveManager.kt:114`).
- 슬롯 초과로 수락이 실패하는 경우(SLOT_EXCEEDED)는 사건이 없다. 1차에서는 제외한다.

## 3. 어떻게 기록하고 보는가

```text
Manager(@Transactional) ── 커밋 ──▶ AFTER_COMMIT 리스너 ── GrowthEventWriter(INFO, category=growth)
                                                               │ stdout JSON
                                                               ▼
                                                     Fluent Bit(FireLens)
                                                     ├─▶ S3 retention=growth/ 90일   (이미 있음, 원본 보관)
                                                     └─▶ HTTP 출력 → PostHog /i/v0/e/ (추가)
```

### 3.1 앱 안에서 기록하는 방법

- **Manager 안에서 Spring 이벤트를 발행하고 `@TransactionalEventListener(AFTER_COMMIT)`에서 로그를 쓴다.**
  16개 모두 같은 방식으로 처리한다.
- outbox 소비자로 붙이지 않는 이유: outbox가 있는 사건이 절반뿐이다. 또 소비자 하나가 실패하면 전체가 다시 전달돼
  중복이 생기고(`OutboxEventConsumer.kt:3`), payload에 없는 값(걸린 시간 등)도 남길 수 없다.
- 감수하는 것: 커밋 직후 프로세스가 죽으면 그 사건 로그가 빠진다. MOI-411 DR-21에서 이미 받아들인 수준이다.
- 로깅 모듈에 필요한 확장(MOI-525에서 이미 "별도 계약"으로 남긴 부분):
  1. `GrowthEventEntry` — `RequestLogEntry`처럼 타입이 있는 엔트리. eventCode 형식 검사, 허용 필드만 받기
  2. `LogSanitizer`에 이 엔트리일 때 `eventCode`·`category=growth`를 채우는 분기
  3. `GrowthEventWriter` — 항상 INFO
  4. `sanitize.lua:2` 허용 필드에 그로스 필드 추가. 지금은 이 목록에 없는 필드를 모두 버려서 ID가 남지 않는다.

### 3.2 관측 도구 — PostHog (2026-10-09 결정)

비교했던 후보(Mixpanel, Athena + QuickSight, Grafana/Metabase + Athena) 중 PostHog로 정했다.
FE와 같은 프로젝트를 쓰므로 프론트 이벤트와 서버 사건이 한 퍼널로 이어진다.

**서버 사건을 넣는 방법: Fluent Bit HTTP 출력 → PostHog 수집 API로 바로 보낸다. 중간 서버와 앱 SDK는 쓰지 않는다.**

- PostHog 수집 서버는 이벤트 **배열**을 그대로 받는다. 각 이벤트 안에 프로젝트 토큰(`api_key`)을 넣으면 된다.
  공식 문서에는 `{api_key, batch:[...]}` 형식만 나오지만, 수집 서버 소스에서 배열 형식(posthog-js가 쓰는 형식)과
  이벤트별 `api_key`·`uuid` 필드를 확인했다
  ([`v0_request.rs`](https://github.com/PostHog/posthog/blob/master/rust/capture/src/v0_request.rs) `RawRequest::Array`,
  [`event.rs`](https://github.com/PostHog/posthog/blob/master/rust/common/types/src/event.rs) `RawEvent`).
  gzip은 본문 앞부분으로 자동 판별한다.
- 그래서 처음 조사에서 걱정했던 "요청을 한 번 감싸야 해서 중계 서버가 필요하다"는 문제는 없다.
- Fluent Bit 쪽 작업:
  1. growth 사건만 고르는 출력 하나를 추가한다(기존 S3 출력은 그대로).
  2. Lua 필터로 한 줄을 PostHog 이벤트 모양으로 바꾼다.
     `{api_key, event, distinct_id, uuid=eventId, timestamp=occurredAt, properties:{...}}`
  3. `http` 출력은 `format json`, `compress gzip`, 주소는 `https://{us|eu}.i.posthog.com/i/v0/e/`.
  4. 프로젝트 토큰은 SSM SecureString에 두고 FireLens 설정에 주입한다.
     이 토큰은 원래 브라우저에 공개되는 쓰기 전용 키지만, 저장소·로그에는 쓰지 않는다.
- dev에서 확인할 것: `format json`이 실제로 배열로 나가는지, 실패 응답에서 Fluent Bit 재시도 동작,
  같은 `uuid`를 다시 보냈을 때 중복이 정리되는지(공식 문서에 중복 제거 언급 없음), 한 요청 20MB 제한.
- S3 원본은 그대로 90일 보관한다. PostHog 쪽 데이터가 빠지면 S3에서 같은 `uuid`로 다시 보낸다.
  과거 데이터를 대량으로 넣을 때는 `historical_migration` 표시가 있는 batch 형식을 쓴다.

**AI 질의·시각화:** PostHog AI로 질문하면 차트·SQL을 만들어 준다. 퍼널·리텐션·대시보드는 기본 기능이다.

**과금(2026-10 확인, 제3자 자료 포함):**

- 월 100만 이벤트까지 무료. 그 뒤로는 구간별 단가가 내려가는 종량제.
- **사람(person profile)에 연결된 이벤트는 익명 이벤트보다 비싸다.** 서버 사건 16개는 모두 로그인 회원의 사건이라
  사람에 연결된 이벤트로 잡힌다. 단가는 자료마다 달라 공식 계산기로 다시 확인해야 한다.
- 서버 사건은 회원 한 명당 월 수십 건 수준이라, 비용의 대부분은 FE 이벤트 양이 결정한다(측정값 아님, 어림).
- 결제 한도(billing limit)를 걸어 두면 예상 밖 과금을 막을 수 있다.

**데이터 위치:** 미국 또는 EU 중 프로젝트 생성 시 고른다(한국 리전 없음). 나중에 바꾸기 어렵다. FE와 같은 곳을 써야 한다.

## 4. 사용자 식별 — 판단

**서버가 회원별 `analyticsId`를 만들어 서버 로그와 FE가 같은 값을 쓴다.**

- 값: `HMAC-SHA-256(키, memberId)`의 앞 128비트를 hex로 표현한 것. 키는 SSM에 두고 키 버전을 함께 남긴다.
  DB 컬럼을 새로 만들지 않고 필요할 때 계산한다.
- FE 전달: 로그인 후 내 정보 응답에 `analyticsId`를 내려준다. FE는 그 값으로 PostHog `identify`를 호출한다.
  - 로그인 전 방문(광고 유입·UTM 포함)은 FE SDK가 익명 ID로 남기고, `identify` 순간 PostHog가 같은 사람으로 합친다.
  - 그래서 **서버가 SID·UTM을 직접 받을 필요가 없다.** MOI-411 DR-12에서 미뤄둔 SID·UTM 계약을 FE SDK에 맡기는 방식이다.
- 원래 memberId를 쓰지 않는 이유: 외부 업체로 나가는 값이 내부 DB 키와 바로 이어지지 않게 하기 위해서다.
  다만 키를 가진 우리는 다시 연결할 수 있으므로 익명화가 아니라 **가명화**다. 개인정보처리방침에서는 여전히 개인정보로 다뤄야 한다.
- 비로그인 서버 사건은 1차에 없다. 1차 16개는 모두 로그인 회원의 행동이다.

## 5. 단계 제안

1. **결정** — 도구는 PostHog로 결정됐다. 지표(1절)·식별 방식(4절)을 확정하고, FE와 PostHog 프로젝트·리전을 맞춘다.
2. **Wiki 반영** — 확정된 내용으로 관측성·로깅 가이드 topic을 갱신한다(`wiki-sync`).
3. **로깅 계약** — `GrowthEventEntry`·Writer·Sanitizer·`sanitize.lua` 허용 필드 확장과 테스트.
4. **사건 16개 기록** — 2절의 코드 보완(가입 구분, 생성 중복 구분, 위임 시 재개 이벤트) 포함.
5. **전송** — Fluent Bit → PostHog 출력(dev 먼저). 토큰은 SSM SecureString으로 관리. Terraform plan까지.
6. **확인** — dev에서 사건 수를 DB와 대조하고, PostHog에서 1.2 퍼널을 만들어 본다.

3~5는 코드·인프라라서 PR을 나누는 게 좋다(앱 로깅 / 사건 기록 / 인프라 전송).

## 6. 열린 결정 → `tbd.md`

## 7. 2차 후보

질문 작성 수, 라운드 피드백 작성률, 클로징 응답률, 방명록 작성, 알림 도달·클릭(클릭 추적은 actionPath에 파라미터 추가가 필요),
첫 기본 이력서의 AI 요약 성공률.
