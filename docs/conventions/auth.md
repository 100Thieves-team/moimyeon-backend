# 인증 구조

[← 허브로](README.md)

원칙: **api 는 security 를 모른다.** 인증 기술(spring-security, JWT, OAuth)은 `security:security-core`
모듈에 격리하고, api 와의 접점은 표준 서블릿 계약(`Principal`)과 포트 인터페이스로 최소화한다.

## 로그인 플로우 (참고)

- 로그인 시작·콜백(`/oauth2/authorization/google`, `/login/oauth2/code/google`)은 컨트롤러가 아니라
  Spring Security 필터 체인이 처리한다.
- local·local-dev·dev 환경은 `POST /v1/auth/dev-sessions`에서 기존 회원 UUID를 받아 Google OAuth 없이
  만료 없는 액세스 토큰을 응답한다. 이 컨트롤러와 발급 컴포넌트는 개발용 프로파일에서만 등록한다.
- Google OAuth 성공 시 환경별 액세스·리프레시 쿠키를 발급한다. 기본·live는
  `ACCESS_TOKEN`·`REFRESH_TOKEN`, dev는 `DEV_ACCESS_TOKEN`·`DEV_REFRESH_TOKEN`을 사용한다.
  API 인증은 쿠키(웹) 또는 `Authorization: Bearer`(앱) 둘 다 허용
  (`HeaderOrCookieBearerTokenResolver`).
- Access Token은 회원 UUID를 subject로, `USER` 또는 `ADMIN`을 `roles` claim으로 담는다.
  Resource Server는 이를 `ROLE_USER`, `ROLE_ADMIN` Spring Security 권한으로 변환한다.
- refresh/logout 경로에서는 액세스 토큰을 해석하지 않는다(만료 토큰이 실려도 401 로 막지 않기 위해).

OAuth 성공·실패 핸들러와 API 401/403 핸들러는 필터 체인의 필수 의존성이다. 환경별 선택 컴포넌트처럼
nullable 기본값으로 받지 않는다. 성공 핸들러가 빠지면 Spring Security 기본 성공 URL(`/`)로
리다이렉트되어 세션 쿠키 발급과 프론트 콜백이 모두 건너뛰어진다. 실패 핸들러가 빠지면 Google 거절이나
state 검증 오류가 백엔드 `/login?error`에 남는다.

- OAuth 성공 리다이렉트는 `security.auth.oauth2.success-redirect-uri`, 실패 리다이렉트는
  `security.auth.oauth2.failure-redirect-uri` 설정을 사용한다. 둘 다 절대 HTTP(S) URI여야 한다.
- 환경별 웹 세션 범위는 프론트와 API가 공유하는 최소 도메인으로 제한한다. `live`는
  `moimyeon.plady.io`, `dev`는 `dev.moimyeon.plady.io`를 사용해 운영·개발 쿠키가 서로 덮어쓰지
  않게 한다. dev OAuth 완료 리다이렉트는 `https://dev.moimyeon.plady.io`를 가리키고, CORS는 이
  preview 오리진과 로컬 개발 예외 `http://localhost:3000`만 허용한다.
- 회원 확정·JWT·세션·쿠키 생성이 전부 성공한 뒤 환경별 액세스·리프레시 쿠키를 응답에 함께 기록한다.
  중간 실패에는 일부 쿠키를 발급하지 않는다.
- Google 오류와 로그인 내부 처리 오류는 모두 고정된 프론트 실패 URI로 보낸다. 공급자 오류 설명과 내부
  예외 메시지는 리다이렉트 URL이나 응답에 싣지 않는다.

## 개발 환경 전용 인증

- `POST /v1/auth/dev-sessions`는 요청한 탈퇴하지 않은 회원을 조회한 뒤 현재 `MemberRole`로 `exp` claim이
  없는 액세스 토큰을 만든다. 성공 응답의 `data.accessToken`으로 반환하며 리프레시 세션을 저장하거나
  쿠키를 발급하지 않는다.
- 회원이 없거나 탈퇴했으면 기존 `MEMBER_NOT_FOUND`(404 E1006)를 사용하며 액세스 토큰을 발급하지 않는다.
- 기존 액세스 쿠키가 만료됐더라도 개발 로그인을 다시 할 수 있도록 refresh/logout과 같이 Bearer Token
  해석 대상에서 제외한다.
- staging·live에는 빈을 등록하지 않으며, 개발용 프로파일과 `live`가 함께 활성화되어도 등록하지 않는다.
  운영 환경에는 같은 경로의 핸들러가 존재하지 않는다.

## 부하테스트 인증

- `PerfAuthenticationFilter`는 별도 부하테스트 환경에서만 `X-Test-User-Id`의 회원 UUID를 신뢰해
  `ROLE_USER` 인증을 만든다. UUID는 부하테스트 DB에 실제로 존재하는 활성 회원이어야 한다.
- 활성화에는 `perf` 프로파일과 `security.perf-auth.enabled=true`가 모두 필요하다. 배포 시
  `SPRING_PROFILES_ACTIVE=dev,perf`, `SECURITY_PERF_AUTH_ENABLED=true`로 주입한다.
- `live` 프로파일이 함께 활성화되면 필터 빈을 만들지 않는다. 부하테스트 환경은 운영 DB·Redis·도메인과
  연결하지 않는다.
- 공통 `SecurityConfig`는 OAuth·401·403 컴포넌트를 필수로 받고, 환경에 따라 존재하지 않을 수 있는
  perf 필터만 `ObjectProvider`로 선택 조립한다.

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

- 회원과 관리자는 같은 `member` 신원을 사용하고 `MemberRole`(`USER`, `ADMIN`)로 권한을 구분한다.
  신규 회원은 항상 `USER`로 시작하며, 관리자 전용 로그인·별도 계정 테이블을 두지 않는다.
- `/admin/**`는 `ROLE_ADMIN`만 접근한다. 미인증 요청은 401, `ROLE_USER`는 403으로 거부한다.
  Admin ArgumentResolver는 표준 `Principal.name`(JWT subject)을 회원 UUID로 변환하며 임시 anonymous 관리자를 만들지 않는다.
- OAuth 로그인과 Refresh Token 재발급은 DB의 현재 회원 역할을 다시 읽어 새 Access Token에 반영한다.
  이미 발급된 JWT는 만료 전까지 역할이 남으므로 권한 변경은 최대 Access Token TTL(30분) 뒤에
  완전히 반영된다. 즉시 회수가 필요해지면 세션 폐기·토큰 차단 정책을 별도로 추가한다.
- 관리자 승격·해제 API와 권한 감사 기록은 아직 제품 요구사항이 없어 범위에서 제외했다.
- 필터 체인은 OAuth·refresh/logout·dev 세션 발급·health와 제품상 공개된 조회 API만 `permitAll`로 열고,
  `/admin/**`는 `ROLE_ADMIN`, 그 외 요청은 `authenticated()`를 요구한다. 관리 엔드포인트는
  health만 공개하며 `/actuator/**`는 명시적으로 거부한다. 새 공개 API는 HTTP 메서드와 경로를
  `SecurityConfig`에 함께 추가하고, 그 외 API는 기본 인증 정책을 그대로 따른다.
- 추가 인가 정책(관리자 외 경로별 규칙)은 확정 시 `SecurityConfig.authorizeHttpRequests` 에 추가하고,
  403(E1103) 케이스를 API 문서에 반영한다 ([api-docs.md](api-docs.md)).

## 미확정 (후속 논의)

- 1단계 통합안: 세션 발급 포트 정리(`SessionPort`)와 `AuthController` 의 security 모듈 이동이
  논의만 된 상태다. 진행 시 이 문서를 갱신한다.
- 관리자 권한을 즉시 회수해야 하는 운영 요구사항과 관리자 승격·해제 감사 기록은 미확정이다.
