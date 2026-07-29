# 예외 처리

[← 허브로](README.md)

## 두 개의 예외 계층

예외를 **어느 레이어의 사건인가**로 나눈다. 위치: `core.support.error`.

| 계층 | 예외 | 에러 타입 | 던지는 곳 |
| --- | --- | --- | --- |
| 도메인 규칙 | `CoreException` | `CoreErrorType` | Service·Implement·VO (닉네임 중복, 약관 미동의, 세션 무효 등) |
| 수송·인증 | `CoreApiException` | `CoreApiErrorType` | 어드바이스·ArgumentResolver·필터 (요청 형태 오류, 인증 필요 등) |

- 두 ErrorType 모두 `(status, code, message, logLevel)` 을 갖는다. 메시지는 사용자에게 그대로
  표시 가능한 한국어로 쓴다.
- **`ErrorCode` 네임스페이스는 공유한다** — 와이어에 나가는 식별자는 하나의 체계다.
  두 enum 의 합집합이 중복·미사용 없이 유지되는지 `ErrorTypeConsistencyTest` 가 강제한다.

## 에러 코드 번호대

- 공통 코드는 `E400`(요청 형태 오류)·`E500`(서버 오류), 인증·인가 계열은 `E11xx` 로 예약되어 있다.
- 도메인 영역별로 **백 단위 번호대**를 할당한다(예: 회원·프로필 `E10xx`, 약관 `E12xx`).
  **번호대의 원본은 문서가 아니라 enum 이다** — 현재 할당은 `CoreErrorType`/`CoreApiErrorType` 의
  배치와 주석으로 관리한다.
- 새 개념을 추가하면 비어 있는 다음 번호대를 잡고, `ErrorCode` 와 ErrorType 항목을 함께 추가한다.
- FE 분기 기준은 HTTP 상태가 아니라 **`error.code`** 다.

## 프리컨디션 함수

표준 `require`/`check` 는 500(fail-fast)으로 이어지므로, **정상 흐름에서 도달 가능한** 규칙 위반은
`DomainPreconditions` 의 함수로 던진다. 구분 기준:

- 정상 흐름에서 도달 가능한 비즈니스 규칙 위반 → `requireBusiness(condition, errorType)` /
  `requireFound(value, errorType)` (→ `CoreException`, 4xx 정상 응답)
- 도달하면 곧 버그인 구조 불변식 → 표준 `require`/`check` (IAE/ISE → 500 fail-fast)

`contract` 가 걸려 있어 호출 이후 스마트 캐스트가 동작한다.

## 전역 핸들링 (`ApiControllerAdvice`)

`@RestControllerAdvice(basePackages = ["io.plady.moimyeon.core"])` — admin 조립과 충돌하지 않게 범위 제한.

| 예외 | 응답 |
| --- | --- |
| `CoreException` / `CoreApiException` | errorType 매핑 (logLevel 따라 ERROR/WARN/INFO 로깅) |
| `MethodArgumentNotValidException` (바디 Bean Validation) | 400 E400 + `error.data` 에 필드별 사유 (같은 필드 다건은 `groupBy` + `joinToString` 집계) |
| `HandlerMethodValidationException` (파라미터 제약) | 400 E400 + 파라미터별 사유 |
| `MissingServletRequestParameterException` | 400 E400 |
| `MethodArgumentTypeMismatchException` | 400 E400 |
| `HttpMessageNotReadableException` (깨진 JSON 등) | 400 E400 |
| 그 외 `Exception` | 500 E500 |

수송 계층 예외를 핸들러 없이 두면 generic 핸들러로 떨어져 **클라이언트 잘못이 500 으로 새어 나간다**.
새 수송 예외 유형을 만나면 E400 계열 핸들러를 추가한다.

필터 레벨(인증/인가 실패)은 어드바이스를 타지 못하므로 `AuthErrorWriter` 가 같은 `ApiResponse`
포맷으로 직접 응답한다 ([auth.md](auth.md)).

## 유니크 충돌(동시성 레이스) 매핑 규칙

확인-후-저장 사이의 레이스는 DB 유니크 제약이 최종 방어선이다. 이때 catch 규칙:

- **기대한 충돌만** 도메인 에러로 매핑한다. 어떤 제약이 터졌는지(재조회 또는 제약명 확인)로 구분한다.
  ```kotlin
  // MemberService.changeNickname: 기대한 닉네임 유니크 충돌(제약명 확인)만 E1007, 그 외는 전파
  catch (e: DataIntegrityViolationException) {
      if (isNicknameConflict(e)) throw CoreException(NICKNAME_DUPLICATED) // uk_member_nickname 확인
      throw e
  }
  ```
- 그 외 무결성 위반(not-null 위반 등)을 삼키면 **오인 매핑**이 된다. 반드시 전파해 500 으로 드러낸다.
- 충돌을 트랜잭션 안에서 드러내야 하면 `save` 대신 `saveAndFlush` 를 쓴다
  (예: `MemberManager.append` — 동시 가입 레이스를 E1004 로 매핑하기 위해).
- 참조 무결성은 DB 가 아니라 애플리케이션이 담당한다 — **FK 제약을 걸지 않으므로**
  ([storage.md](storage.md)), 존재하지 않는 참조 id 는 **저장 전에 명시 검증**해 전용 코드로
  응답한다(예: 존재하지 않는 직무 id → `JOB_ROLE_NOT_FOUND`).

## 응답 포맷

모든 에러 응답은 같은 봉투를 쓴다 ([api-design.md](api-design.md)):

```json
{ "result": "ERROR", "data": null, "error": { "code": "E1007", "message": "이미 사용 중인 닉네임입니다.", "data": null } }
```

`error.data` 는 추가 정보(검증 오류의 필드별 사유 등)에만 쓴다. 예외 케이스는 API 문서에
에러 코드와 함께 정의한다 ([api-docs.md](api-docs.md)).
