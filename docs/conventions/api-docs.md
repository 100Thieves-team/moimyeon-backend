# API 문서화

[← 허브로](README.md)

원칙: **문서는 테스트가 만든다.** 손으로 쓴 스펙은 코드와 어긋난다 — 문서화 테스트가 실제 요청/응답을
캡처해 스니펫과 OpenAPI 스펙을 생성한다.

## 파이프라인

```text
RestDocs 테스트 (@Tag("restdocs"), documentApi(...))
  ├─▶ build/generated-snippets/{identifier}/*.adoc ─▶ src/docs/asciidoc/index.adoc ─▶ index.html
  └─▶ restdocs-api-spec 리소스 ─▶ :core:core-api:openapi3 ─▶ build/api-spec/openapi3.yaml
        └─▶ 직전 gh-pages 스펙과 operation 비교 ─▶ Slack 변경 알림 + Swagger UI 자동 배포
```

- 같은 경로·메서드의 `document()` 호출은 **하나의 OpenAPI 연산으로 병합**된다. 상태 코드별 응답,
  identifier 별 named example 로 정리된다.
- 로그인 플로우는 컨트롤러가 아니라 필터 체인 처리라 일반 `RestDocsTest` 스니펫을 만들지 않는다.
  핸들러 단위 테스트와 필터 체인 컨텍스트 테스트로 동작을 고정하고, index.adoc 수기 섹션과
  OpenAPI 생성 후 보정 경로(`/oauth2/authorization/google`, `/login/oauth2/code/google`)로 문서화한다.
  OpenAPI에는 웹 쿠키(`AccessTokenCookie`)와 앱 Bearer(`BearerAuth`) securityScheme도 함께 선언한다.

## API 스펙 변경 알림

- dev/main에서 API Docs Pages 워크플로가 새 스펙을 만들고, 같은 브랜치의 직전 gh-pages 스펙과 비교한다.
- HTTP method·path 추가/삭제와 operation 본문 변경을 알린다. operation이 로컬 `$ref`로 참조하는
  request·response·parameter·schema 변경도 해당 API의 변경으로 판정한다.
- 실행마다 달라질 수 있는 `example`·`examples`와 문서 표현인 `summary`·`description`은
  계약 변경 판정에서 제외한다.
- 로컬 component의 `$ref` 이름은 생성 순서에 따라 바뀔 수 있으므로 계약으로 보지 않고,
  참조를 확장한 실제 내용과 재귀 연결 구조를 비교한다.
- 비교 기준선이 아직 없으면 최초 스펙을 게시하되 전체 API를 신규 변경으로 알리지 않는다.
- 변경 목록은 repository secret `SLACK_API_SPEC_WEBHOOK_URL`의 Incoming Webhook으로 보낸다.
  시크릿 값과 채널 설정은 저장소 밖에서 관리한다.
- Slack 변경 알림은 dev에서만 보낸다. main은 API 문서를 게시하되 같은 계약을 다시 알리지 않는다.
- Slack 전송 실패는 API 문서 게시를 막지 않고 Actions 경고로 남긴다.

## 문서화 테스트 작성법

`RestDocsTest`(tests/api-docs) 상속. standalone MockMvc 라 스프링 컨텍스트 없이 돈다.

```kotlin
mockMvc = mockController(
    OrderController(orderService),                       // mockk Service
    LoginMemberArgumentResolver(),
    controllerAdvice = ApiControllerAdvice(),            // 에러 응답 문서화에 필요
)
```

- `documentApi(identifier, summary, description, ...snippets)` 로 문서화한다.
- **summary·description 은 같은 연산의 모든 테스트(성공+예외)가 공유**해야 하므로 클래스 상단에
  상수로 추출한다(병합 시 어느 모델이 뽑혀도 동일하게).
- description 에는 그 API 가 응답하는 **예외 케이스를 에러 코드와 함께** 서술한다.
- 성공 케이스는 `requestFields`/`responseFields` 로 필드를 전부 문서화한다(누락 필드는 테스트 실패).

## 예외 케이스 문서화 (필수)

**모든 API 는 성공뿐 아니라 예외 응답도 스펙에 정의한다.** 방법: 예외를 실제로 발생시키는
문서화 테스트를 추가한다.

- identifier 규칙: `{operation}-e{코드소문자}` (예: `updateProfile-e1301`, `searchCompanies-e400`).
- 응답 스니펫은 공용 `errorResponseFields()` 를 쓴다(에러 봉투는 전 API 동일).
- 예외 발생 방법:
  - 도메인 에러: mockk Service 가 `CoreException(errorType)` 던지게
  - 검증 에러(E400): 실제로 깨진 요청을 보냄 (bio 501자, 파라미터 누락 등)
  - 인증 없음(E1102): principal 없이 호출 (ArgumentResolver 가 던짐)
  - 토큰 무효(E1102): 운영과 같은 조립의 리소스서버 필터를 `filters = listOf(...)` 로 끼워 호출
- 커버 기준: **그 API 의 description·index.adoc 표에 적힌 모든 에러 코드는 문서화 테스트가 있어야 한다**
  (같은 상태 코드에 여러 코드가 있으면 코드별로 테스트 → 코드별 example 로 병합됨).
- 403(E1103)처럼 아직 트리거할 수 없는 케이스는 공통 규약 문서에만 두고, 정책 확정 후 테스트를 추가한다.

## index.adoc 구성

`core/core-api/src/docs/asciidoc/index.adoc`.

- 상단 "Error Responses (공통 에러 규약)": 에러 봉투 형식(`error.code` 가 FE 분기 기준),
  전 API 공통(E400/E500, 인증 401 E1102) 안내, 실응답 예시 include.
  **에러 코드 전체 표를 한곳에 모으지 않는다.**
- 각 API 섹션: Curl Request / Request·Response Fields / Http Response include + **Error Responses 표**
  (`HTTP | error.code | 케이스`). 예외가 없는 API 는 표를 생략한다.
- 새 엔드포인트 추가 시 index.adoc 에 섹션 추가를 잊지 않는다(스니펫은 생성돼도 include 는 수동).

## 체크리스트 (새 API 추가 시)

1. 성공 문서화 테스트 (`requestFields`/`responseFields` 전체)
2. 예외 케이스별 문서화 테스트 (`{op}-e{code}`)
3. summary/description 상수 공유 + description 에 에러 코드 서술
4. index.adoc 섹션 + Error Responses 표
5. `./gradlew :core:core-api:openapi3` 로 4xx 병합 확인
