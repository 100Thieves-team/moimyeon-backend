# API 설계

[← 허브로](README.md)

## URI

- 형식: `/v{version}/{리소스}` (예: `/v1/members/me/profile`). `/api` 프리픽스는 쓰지 않는다.
- 버저닝은 경로 기반(`/v1`). 리소스는 복수형, kebab-case(`/v1/job-roles`).
- 인증 주체 자원은 `/v1/members/me/...`, 타인 조회는 `/v1/members/{memberId}/...`.
- Parameter/Body 는 camelCase.

## DTO

위치: `core.api.controller.v1.request` / `response`. **`Dto` 접미사 금지.**

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| 요청 | `[동사][대상]Request` | `CreateProfileRequest` |
| 응답 | `[대상]Response` / `[대상][용도]Response` | `ProfileResponse`, `CompanySearchResponse` |

- DTO 는 파일당 하나. 단, 응답 구조상 강결합된 하위 DTO 는 같은 파일에 둘 수 있다
  (`RegionsResponse` + `SidoResponse` + `SigunguResponse`).

### 변환 방향

- **요청 → 도메인**: 요청 DTO 의 `toXxx()` 메서드. **변환 결과(행위 입력 값)는 식별자를 담지
  않는다** — 인증 주체 id 는 컨트롤러가 Service/Facade 호출 인자로 따로 넘긴다.
  ```kotlin
  data class UpdateProfileRequest(...) {
      fun toContent(): ProfileContent = ProfileContent(...)
  }
  // 컨트롤러: profileFacade.update(currentMember.id, request.toContent())
  ```
- **도메인 → 응답**: 응답 DTO 의 `companion object` 정적 팩토리 `from(...)`/`of(...)`.
  파라미터는 **도메인 객체 또는 풀어낸 필드**만 받는다. **Request 타입을 받지 않는다**
  (응답이 요청 표현에 의존하면 안 됨).
  ```kotlin
  data class ProfileResponse(...) {
      companion object {
          fun from(profile: MemberProfile, interestCompanies: List<Company>): ProfileResponse { ... }
      }
  }
  ```

## 검증: 3계층

검증은 "누구의 규칙인가"로 나눈다. 같은 규칙을 두 군데서 검증하지 않는다.

| 계층 | 수단 | 실패 응답 | 예 |
| --- | --- | --- | --- |
| 수송(요청 형태) | Bean Validation (`@Valid` 바디, 파라미터 제약 애노테이션) | 400 `E400` + 필드별 사유 | `bio @Size(max = 500)`, `query @Size(min = 1, max = 50)` |
| 값(도메인 VO) | VO 생성 시점 보증 | 도메인 코드 (400 `E1005` 등) | `Nickname` 형식·길이·금칙어 |
| 교차 규칙 | Service 흐름 (`requireBusiness`) | 도메인 코드 (409 등) | 약관 동의, 닉네임 중복, 참조 존재 |

- 수송 계층 검증은 **스키마 제약의 반영**이다(DB 컬럼 길이 등). 도메인 규칙(닉네임 형식)은
  VO 소관이므로 요청 DTO 에 중복 정의하지 않는다.
- **컨트롤러 클래스에 `@Validated` 를 붙이지 않는다.** Spring 6.1+ 는 핸들러 파라미터 제약을
  내장 메서드 검증으로 처리하며(`HandlerMethodValidationException` → E400), `@Validated` 가 있으면
  내장 검증이 꺼지고 AOP 경로(`ConstraintViolationException`, 미처리 500)로 바뀐다.
- `@Validated` 는 컨트롤러가 아닌 일반 빈의 메서드 검증에만 의미가 있는데, 이 코드베이스는
  그 용법을 쓰지 않는다(도메인 규칙은 VO·Implement 가 보증).

## 응답 포맷

모든 응답은 `ApiResponse<T>` 로 감싼다 (`core.support.response`).

```kotlin
class ApiResponse<T> private constructor(
    val result: ResultType,     // SUCCESS | ERROR
    val data: T? = null,
    val error: ErrorMessage? = null,
)
```

- 생성자는 `private`, 팩토리는 `success()` / `success(data)` / `error(errorType, data)`.
- 응답 body 는 최소 스펙(YAGNI). Boolean 에 null 금지, 제한 문자열은 Enum, 복수형 빈 값은 빈 배열.
- id 참조 응답 원칙: FE 가 카탈로그 목록을 이미 갖고 있으면 id 만 내려준다(`jobRoleId`, `sigunguId`).
  FE 가 목록을 갖고 있지 않은 검색 기반 데이터는 라벨을 함께 조립한다(`{companyId, name}`).

## Enum

- 도메인 전역 공유 Enum 은 `core:core-enum` (`io.plady.moimyeon.core.enums`)에 둔다
  (예: `MemberStatus`).
- 제한된 문자열 값은 항상 Enum 으로 표현하고 `@Enumerated(EnumType.STRING)` 으로 저장한다.

## 모킹 API 패턴

새 기능은 **API 계약(모킹)을 먼저 배포**하고 FE 와 병렬로 진행한다.

- 모킹 컨트롤러에 `@MockApiProfile` 을 붙인다 — `@Profile("local", "local-dev", "dev")` 라
  운영(live)에는 빈 자체가 등록되지 않는다. 어노테이션은 기능마다 재사용한다.
- 모킹은 **정적 목업 값**을 반환한다(figma 확정안 값). 실제처럼 동적으로 동작하게 만들지 않는다 —
  모킹임이 드러나야 실구현과 혼동되지 않는다.
- 실구현 전환 시 URI·응답 계약은 유지하고 모킹 스텁만 제거한다. RestDocs 문서/테스트는
  실구현 기준으로 교체한다.
- 일부만 실구현 가능한 API(예: 공개 프로필의 신뢰 지표)는 컨트롤러는 유지하되 목업 값 고정 반환을
  문서에 명시한다.
