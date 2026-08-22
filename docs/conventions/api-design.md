# API 설계

[← 허브로](README.md)

## URI

- 형식: `/v{version}/{리소스}` (예: `/v1/members/me/profile`). `/api` 프리픽스는 쓰지 않는다.
- 버저닝은 경로 기반(`/v1`). 리소스는 복수형, kebab-case(`/v1/job-roles`).
- 인증 주체 자원은 `/v1/members/me/...`, 타인 조회는 `/v1/members/{memberId}/...`.
- Parameter/Body 는 camelCase.
- **포함 관계만 path 로 중첩한다.** 하위 개념이 상위에 수명 종속이면 중첩
  (`/v1/rooms/{roomId}/questions`), 늘어날 수 있는 조회 조건이면 RequestParam
  (`/v1/rooms?sigunguId=`). 판정 기준: 조건이 하나 추가될 때 path 가 그대로면 필터다.
- **path 의 리소스 ID 는 1개까지** — `/a/{aId}/b/{bId}` 를 만들지 않는다. 상위 ID 가
  있어야만 하위를 찾을 수 있다면 하위 ID 가 유니크하지 않다는 설계 신호다.
- **URI 는 호출 주체가 아니라 제공 정보를 표현한다.** `/summary`·`/detail` ○,
  `/by-admin`·`/for-user` ✗ — 권한이 늘어도 URI 가 재사용되어야 한다.
- **CRUD 로 표현되지 않는 도메인 행위는 동사를 쓰되 `/리소스/{id}/행위` 템플릿을
  지킨다** (`POST /v1/rooms/{roomId}/participations/cancel`). 삭제와 취소는 다른 행위다.

## DTO

위치: `core.api.controller.v1.request` / `response`. **`Dto` 접미사 금지.**

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| 요청 | `[동사][대상]Request` | `CreateOrderRequest` |
| 응답 | `[대상]Response` / `[대상][용도]Response` | `OrderResponse`, `ProductSearchResponse` |

- DTO 는 파일당 하나. 단, 응답 구조상 강결합된 하위 DTO 는 같은 파일에 둘 수 있다
  (`OrderResponse` + `OrderLineResponse`).

### 변환 방향

- **요청 → 도메인**: 요청 DTO 의 `toXxx()` 메서드. **변환 결과(행위 입력 값)는 인증 주체
  식별자를 담지 않는다** — `currentMember.id`는 컨트롤러가 Service/Facade 호출 인자로 따로
  넘긴다. 요청으로 선택한 `productIds` 같은 다른 개념의 참조 식별자는 행위 입력에 포함할 수 있다.
  ```kotlin
  data class CreateOrderRequest(val productIds: List<Long>, ...) {
      fun toContent(): OrderContent = OrderContent(productIds = productIds, ...)
  }
  // 컨트롤러: orderService.place(currentMember.id, request.toContent())
  // 단일 Service 호출이면 컨트롤러가 직접 부른다. Facade 는 여러 Service 결과를 조합할 때만.
  ```
- **도메인 → 응답**: 응답 DTO 의 `companion object` 정적 팩토리 `from(...)`/`of(...)`.
  파라미터는 **도메인 객체 또는 풀어낸 필드**만 받는다. **Request 타입을 받지 않는다**
  (응답이 요청 표현에 의존하면 안 됨).
  ```kotlin
  data class OrderResponse(...) {
      companion object {
          fun from(order: Order, products: List<Product>): OrderResponse { ... }
      }
  }
  ```

## 검증: 어디서 확정되는가

> **경계를 넘는 순간 온전한 개념 객체여야 한다.**
> 요청 DTO 는 API 스펙이고, `toXxx()` 가 그것을 개념 객체로 바꾸는 유일한 지점이다.
> 그 전환이 끝나면 뒤쪽 레이어에는 스펙 검증이 남지 않는다.

**Bean Validation 을 쓰지 않는다.** `@Valid`·`@field:Size`·`@Validated` 모두 금지이며
`spring-boot-starter-validation` 의존성도 두지 않는다. 스펙을 애노테이션에 흩어놓으면
개념 객체는 빈 그릇이 되고, 스펙이 어디까지인지가 테스트가 아니라 리플렉션에 숨는다.

| 무엇 | 어디서 | 실패 응답 | 예 |
| --- | --- | --- | --- |
| **값 하나의 형식·범위** (API 스펙) | 요청 DTO 의 `toXxx()` / 컨트롤러 파라미터 | 400 `E400` (`CoreApiException`) | 메모 최대 500자, `query` 1~50자 |
| **값의 도메인 규칙** | 값 객체 생성 시점 | 도메인 코드 (400 `E1005` 등) | 주문번호 형식, 금칙어 |
| **개념 객체의 성립 조건** | 개념 객체 `init { require(...) }` | 도메인 코드 | 필드 간 정합, 참조 관계 불변식 (주문에 주문라인 최소 1개, 최소·최대 수량의 대소) |
| **DB 를 봐야 하는 규칙** | 그 데이터를 다루는 쓰기 Implement 안 (다른 개념의 판정이면 그 개념의 `Validator`) | 도메인 코드 (409 등) | 값 중복은 그 쓰기 Manager 안, 다른 개념의 참조 유효성은 그 개념의 `Validator` |
| 본문 해석·타입 불일치·파라미터 누락 | 프레임워크 → `ApiControllerAdvice` | 400 `E400` | 깨진 JSON, UUID 아닌 경로 변수 |

```kotlin
data class CreateOrderRequest(
    val productIds: List<Long> = emptyList(),
    val memo: String? = null,
    ...
) {
    fun toContent(): OrderContent {
        if (memo != null && memo.length > MEMO_MAX_LENGTH) throw CoreApiException(CoreApiErrorType.INVALID_REQUEST)

        return OrderContent(...)
    }
}
```

- **값 하나면 프레젠테이션, 관계면 개념 객체, DB 를 봐야 하면 Implement.** 길이·형식처럼 그 값만 보면
  판정되는 것은 API 스펙이므로 DTO 가 확정한다. 여러 필드의 관계나 객체 간 참조에서 나오는 규칙은 그
  개념 객체 안에서 판정한다. 조회가 필요하면 그 데이터를 다루는 쓰기 Implement 가 Repository 를
  직접 보고 검증하고, **다른 개념의 판정을 물어야 할 때만** 그 개념의 `Validator` 를 쓴다
  ([layers.md](layers.md)).
- **검증 실패는 `if (...) throw` 로 쓴다.** 별도 헬퍼를 만들지 않는다 — 스펙이 그대로 읽히는 것이 중요하다.
- **개념 객체의 필드에 기본값을 두지 않는다.** 기본값은 "안 받는다"와 "빠뜨렸다"를 구분 불가능하게 만든다.
  받지 않는 값은 `toXxx()` 에서 `emptyList()` 를 명시적으로 넘겨 의도를 남긴다.
- **목킹 단계라 변환할 개념 객체가 아직 없으면** DTO 에 `validate()` 를 두고 컨트롤러가 호출한다.
  도메인이 붙으면 `toXxx()` 안으로 옮긴다.
- **스펙은 RestDocs 테스트가 문서로 만든다.** 값 규칙 위반 케이스를 컨트롤러 문서화 테스트에 두면
  openapi3.yaml 의 4xx 예시로 나간다([api-docs.md](api-docs.md)). **요청 DTO 단위 테스트를 따로 만들지
  않는다** — 같은 규칙을 두 층에서 검증하면서 문서에는 실리지 않는다.

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
  (예: 여러 모듈이 함께 쓰는 상태 enum).
- 제한된 문자열 값은 항상 Enum 으로 표현하고 `@Enumerated(EnumType.STRING)` 으로 저장한다.

## 모킹 API 패턴

새 기능은 **API 계약(모킹)을 먼저 배포**하고 FE 와 병렬로 진행한다.

- 모킹 컨트롤러에는 `@Profile("local", "local-dev", "dev")` 을 붙여 운영(live)에 빈 자체가
  등록되지 않게 한다. 같은 기능에서 여러 컨트롤러가 공유할 때만 기능별 메타 어노테이션으로 묶는다.
- 모킹은 **정적 목업 값**을 반환한다(figma 확정안 값). 실제처럼 동적으로 동작하게 만들지 않는다 —
  모킹임이 드러나야 실구현과 혼동되지 않는다.
- 실구현 전환 시 URI·응답 계약은 유지하고 모킹 스텁만 제거한다. RestDocs 문서/테스트는
  실구현 기준으로 교체한다.
- 일부만 실구현 가능한 API는 컨트롤러는 유지하되 목업 값 고정 반환 범위를 문서에 명시한다.
