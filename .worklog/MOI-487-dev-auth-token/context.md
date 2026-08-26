# MOI-487 컨텍스트

## 이슈 요약

[MOI-487 개발 전용 인증 api 개선](https://linear.app/100-thieves/issue/MOI-487/개발-전용-인증-api-개선)은 로컬 개발자가 도메인 제한 때문에 개발 로그인 쿠키를 직접 추출하는 불편을 없애려는 작업이다. 현재 `POST /v1/auth/dev-sessions`가 쿠키로 액세스·리프레시 자격 증명을 발급하는 대신, 유효 기간이 없는 액세스 토큰을 응답으로 돌려줘 환경 변수와 Bearer 헤더에 바로 사용할 수 있어야 한다. 이슈에는 추가 코멘트·첨부·관련 이슈가 없고, 상태는 In Progress다.

## 관련 문서

- [회원 및 프로필 PRD](https://app.notion.com/p/ab01bb8f1fbe8360a85081df07d5d50f): 제품 로그인은 Google OAuth이며 자체 이메일·비밀번호 로그인은 제공하지 않는다. MOI-487은 이 제품 계약이 아니라 개발 전용 우회 경로만 바꾼다.
- [2026-08-12 멘토링](https://app.notion.com/p/3ba1bb8f1fbe80d0b03ffff795e54abc): API 개발 단계 인증 편의를 위해 내부 환경에서는 토큰 수명을 길게 둘 수 있지만 외부 환경은 엄격하게 분리한다는 맥락이 있다.
- `docs/conventions/auth.md`: 개발 인증 엔드포인트의 프로파일 격리, 활성 회원 조회, 현재 역할 반영, Bearer 허용 정책의 저장소 기준이다.
- Notion에서 `MOI-487`과 개발 로그인 키워드로 검색했지만 이 이슈 전용 PRD·결정 문서는 찾지 못했다.

## 요구사항 핵심

- 기존 URI와 요청 `memberId` 계약을 유지한다.
- 탈퇴하지 않은 기존 회원의 현재 `MemberRole`을 토큰에 반영한다.
- 액세스 토큰은 JWT `exp` claim 없이 발급한다.
- 성공 시 쿠키가 아니라 `ApiResponse.data.accessToken`으로 반환한다.
- 리프레시 세션을 만들거나 액세스·리프레시 쿠키를 기록하지 않는다.
- 회원 없음·탈퇴는 기존 404 E1006, 잘못된 요청 형식은 400 E400을 유지한다.
- local·local-dev·dev에서만 동작하고 staging·live에는 핸들러를 등록하지 않는다.

## 관련 코드

- `core/core-api/src/main/kotlin/io/plady/moimyeon/core/api/controller/v1/DevAuthController.kt`: 현재 두 쿠키를 `Set-Cookie` 헤더에 기록하고 빈 성공 응답을 반환한다.
- `core/core-api/src/main/kotlin/io/plady/moimyeon/core/api/auth/DevSessionCookieIssuer.kt`: 활성 회원 조회 후 30분 액세스 JWT, 리프레시 세션, 두 쿠키를 조립한다.
- `security/security-core/src/main/kotlin/io/plady/moimyeon/security/auth/JwtTokenProvider.kt`: 모든 액세스 JWT에 현재 시각 기준 30분 `exp`를 넣는다.
- `security/security-core/src/main/kotlin/io/plady/moimyeon/security/config/JwtConfig.kt`: 동일 HMAC 키의 Nimbus encoder/decoder를 제공한다.
- `security/security-core/src/main/kotlin/io/plady/moimyeon/security/config/SecurityConfig.kt`: 개발 세션 경로를 permitAll과 Bearer 해석 제외 경로로 유지한다.
- `core/core-api/src/test/kotlin/io/plady/moimyeon/core/api/controller/v1/DevAuthControllerTest.kt`: 현재 쿠키 성공 계약과 E1006/E400 REST Docs를 고정한다.
- `core/core-api/src/test/kotlin/io/plady/moimyeon/core/api/auth/DevSessionCookieIssuerTest.kt`: 토큰·세션·쿠키의 순차 발급과 부분 쿠키 방지를 고정한다.
- `core/core-api/src/test/kotlin/io/plady/moimyeon/core/api/auth/DevAuthProfileContextTest.kt`: 개발 프로파일 격리를 고정한다.
- `core/core-api/src/test/kotlin/io/plady/moimyeon/core/api/auth/AdminAuthorizationContextTest.kt`: 실제 보안 필터에서 Bearer JWT 인증을 검증한다.
- `core/core-api/src/docs/asciidoc/index.adoc`: 현재 환경별 쿠키 사용법을 안내한다.

## 작업 경계

- 운영 Google OAuth 로그인과 일반 액세스 토큰의 30분 TTL은 바꾸지 않는다.
- refresh/logout API와 세션 저장 정책은 바꾸지 않는다.
- SecurityConfig의 공개·보호 경로와 쿠키/Bearer 병행 인증 정책은 바꾸지 않는다.
- 데이터베이스 스키마·엔티티·마이그레이션은 건드리지 않는다.
- staging·live에서 개발 로그인 엔드포인트를 노출하지 않는다.
