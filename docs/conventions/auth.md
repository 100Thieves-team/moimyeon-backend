# 인증 구조

[← 허브로](README.md)

원칙: **api 는 security 를 모른다.** 인증 기술(spring-security, JWT, OAuth)은 `security:security-core`
모듈에 격리하고, api 와의 접점은 표준 서블릿 계약(`Principal`)과 포트 인터페이스로 최소화한다.

## 로그인 플로우 (참고)

- 로그인 시작·콜백(`/oauth2/authorization/google`, `/login/oauth2/code/google`)은 컨트롤러가 아니라
  Spring Security 필터 체인이 처리한다.
- 성공 시 `ACCESS_TOKEN`(JWT)·`REFRESH_TOKEN` 쿠키 발급. API 인증은 쿠키(웹) 또는
  `Authorization: Bearer`(앱) 둘 다 허용 (`HeaderOrCookieBearerTokenResolver`).
- refresh/logout 경로에서는 액세스 토큰을 해석하지 않는다(만료 토큰이 실려도 401 로 막지 않기 위해).

## 컨트롤러에서 인증 주체 받기

```kotlin
@PostMapping("/v1/orders")
fun createOrder(
    @LoginMember currentMember: CurrentMember,
    @RequestBody request: CreateOrderRequest,
): ApiResponse<OrderResponse>
```

- `@LoginMember CurrentMember` 로 주입받는다. `CurrentMember` 는 api 소유의 단순 값
  (`id: UUID`)이다.
- `LoginMemberArgumentResolver` 는 **표준 `Principal`** 만 읽는다:
  `webRequest.userPrincipal?.name` = 회원 UUID 문자열 (security 필터가 보장하는 계약).
  spring-security 타입은 등장하지 않는다.
- Principal 이 없거나 UUID 가 아니면 `CoreApiException(AUTHENTICATION_REQUIRED)` → 401 E1102.
  이 덕에 인증 필터가 켜져 있지 않아도(permitAll) 인증 필요 API 는 401 로 방어된다.
- 새 ArgumentResolver 는 `WebConfig.addArgumentResolvers` 에 등록한다.

## 포트/어댑터: security ↔ core

security-core 는 core 의 구체 로직을 모른다. 필요한 것은 **포트(인터페이스)로 선언**하고,
core-api 가 어댑터를 구현해 빈으로 제공한다 (`core.api.auth`).

| 포트 (security-core) | 어댑터 (core-api) | 책임 |
| --- | --- | --- |
| `SocialMemberResolver` | `SocialMemberResolverAdapter` | 소셜 신원 → 회원 조회/가입 (`SocialAuthService` 위임) |
| `SessionIssuer` | `SessionIssuerAdapter` | 로그인 성공 시 세션 발급 (`SessionService` 위임) |
| `AuthErrorWriter` | `ApiResponseAuthErrorWriter` | 필터 레벨 401/403 을 공통 `ApiResponse` 포맷으로 응답 |

- 필터에서 발생한 인증 실패는 `@RestControllerAdvice` 를 타지 못하므로,
  `ApiResponseAuthenticationEntryPoint`/`ApiResponseAccessDeniedHandler` 가 `AuthErrorWriter` 로
  직접 응답을 쓴다. 에러 코드는 E1102(401)/E1103(403) ([errors.md](errors.md)).
- 세션 저장(리프레시 토큰) 로직은 `core.domain.session` 소유다. security 는 발급을 요청할 뿐
  저장 방식을 모른다.

## 세션 인증 규칙

- 리프레시 자격 증명으로 활성 세션을 찾고, 그 세션이 가리키는 회원이 현재 존재해야 인증된다.
- 탈퇴 회원은 `MemberFinder`의 활성 회원 조회 기준에서 존재하지 않는 회원이다. 활성 세션 행이
  남아 있어도 인증하지 않는다.
- 세션 없음·만료·폐기와 탈퇴 회원 참조는 모두 `INVALID_SESSION`(401 E1104)으로 응답한다.
  내부의 회원 존재 여부를 `MEMBER_NOT_FOUND` 등으로 구분해 외부에 노출하지 않는다.
- logout은 같은 자격 증명으로 반복 호출해도 최초 폐기 시각을 유지하는 멱등 행위다.

## 인가 정책

- 현재 필터 체인은 공개 경로(oauth, refresh/logout, terms)만 명시하고 나머지는
  `permitAll`(TODO) 상태다. 실질 방어선은 ArgumentResolver 의 E1102.
  이 상태에서는 `@LoginMember` 를 선언하지 않은 핸들러가 익명에게 열리므로, 보호가 필요한
  엔드포인트는 반드시 `@LoginMember` 를 받아야 한다. 인가 정책 확정 시 `authenticated()` 전환으로
  이 임시 방어를 대체한다.
- 인가 정책(경로별 규칙, 역할)은 확정 시 `SecurityConfig.authorizeHttpRequests` 에 추가하고,
  403(E1103) 케이스를 API 문서에 반영한다 ([api-docs.md](api-docs.md)).

## 미확정 (후속 논의)

- 1단계 통합안: 세션 발급 포트 정리(`SessionPort`)와 `AuthController` 의 security 모듈 이동이
  논의만 된 상태다. 진행 시 이 문서를 갱신한다.
- 어드민 인증(별도 필터 체인 3존 구성)은 설계 논의만 있었다.
