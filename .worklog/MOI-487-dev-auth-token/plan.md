# MOI-487 개발 전용 인증 API 개선 계획

- 브랜치: `feat/MOI-487-dev-auth-token`
- 워크트리: `.worktrees/moi-487-dev-auth-token`
- 기준: `origin/dev` (`a92b4ff8`)
- 검증한 커밋: 없음

## 체크포인트

- [x] 체크포인트 A: 구현 계획 승인 (2026-08-27, 쿠키·리프레시 세션을 제거하고 무기한 액세스 토큰으로 완전 전환)
- [x] 체크포인트 B: 테스트 스켈레톤 승인 (2026-08-27)
  - RED 확인 (2026-08-27): `issueWithoutExpiration`과 `DevAccessTokenIssuer` 미구현으로 security-core·core-api 테스트 컴파일 실패
  - 스타일 확인 (2026-08-27): 변경 모듈 `ktlintCheck` 통과
- [x] 체크포인트 C: 구현 및 검증 승인 (2026-08-27)
  - 코드 리뷰 통과 (2026-08-27): 필수 1건인 `dev,staging` 혼합 프로파일 노출을 수정하고 재리뷰 통과
  - 전체 게이트 통과 (2026-08-27): `./gradlew test ktlintCheck :core:core-api:openapi3`
  - OpenAPI 확인 (2026-08-27): 200 응답에 `data.accessToken` 포함, `Set-Cookie` 응답 헤더 없음
- [ ] 커밋·PR 완료

## 요구사항 흐름

개발 프로파일에서 회원 UUID를 받으면 탈퇴하지 않은 회원과 현재 역할을 조회하고, `exp` claim이 없는 개발용 액세스 토큰을 발급해 응답 본문의 `accessToken`으로 반환한다. 회원 조회나 토큰 발급이 실패하면 응답 토큰을 만들지 않으며, 리프레시 세션·쿠키처럼 서버나 클라이언트에 부분 상태를 남기지 않는다.

## 접근 방식

1. 운영 액세스 토큰의 30분 TTL 발급 경로는 그대로 두고, `JwtTokenProvider`에 만료 claim을 넣지 않는 명시적인 개발용 발급 경로를 추가한다.
2. `DevSessionCookieIssuer`를 개발용 액세스 토큰 발급 책임으로 축소한다. 활성 회원 조회와 현재 역할 반영만 수행하고 `SessionIssuer`·`AuthCookieFactory` 의존성과 리프레시 세션 저장을 제거한다.
3. `POST /v1/auth/dev-sessions`의 성공 계약을 빈 응답+`Set-Cookie`에서 `ApiResponse<DevAccessTokenResponse>`의 `data.accessToken`으로 교체한다. URI·요청 DTO·프로파일 가드·E1006/E400 오류 계약은 유지한다.
4. 발급된 무기한 토큰이 실제 `Authorization: Bearer` 인증 필터에서 허용되는지 컨텍스트 테스트로 검증한다. 운영 토큰에는 기존 `exp`가 계속 포함되는 회귀 테스트도 둔다.
5. REST Docs와 인증 컨벤션 문서를 새 응답 계약과 무상태 개발 로그인 흐름에 맞춘다.

## 예상 변경 지점

- `security/security-core/src/main/kotlin/.../JwtTokenProvider.kt`: 만료 없는 명시적 발급 경로 추가, 기존 TTL 경로 보존
- `security/security-core/src/test/kotlin/.../JwtTokenProviderTest.kt`: `exp` 유무와 공통 subject/roles claim 검증
- `core/core-api/src/main/kotlin/.../auth/DevSessionCookieIssuer.kt`: 토큰 전용 발급 컴포넌트로 이름·의존성·반환값 정리
- `core/core-api/src/main/kotlin/.../controller/v1/DevAuthController.kt`: 응답 body로 액세스 토큰 반환, 쿠키 헤더 제거
- `core/core-api/src/main/kotlin/.../controller/v1/response/`: 개발용 액세스 토큰 응답 DTO 추가
- `core/core-api/src/test/kotlin/.../auth/DevSessionCookieIssuerTest.kt`: 활성 회원의 현재 역할 기반 무기한 토큰 발급 스펙으로 교체
- `core/core-api/src/test/kotlin/.../auth/DevAuthProfileContextTest.kt`: 축소된 빈 의존성과 프로파일 격리 검증
- `core/core-api/src/test/kotlin/.../auth/AdminAuthorizationContextTest.kt`: 무기한 Bearer 토큰 인증 가능 검증
- `core/core-api/src/test/kotlin/.../controller/v1/DevAuthControllerTest.kt`: 응답 필드·쿠키 부재·오류 REST Docs 계약 검증
- `core/core-api/src/docs/asciidoc/index.adoc`, `docs/conventions/auth.md`: 사용법과 정책 갱신

실제 이름 변경 범위는 테스트 스켈레톤에서 기존 참조를 확인한 뒤 최소화한다.

## 만들 테스트

### security

- 일반 액세스 토큰은 subject·roles와 30분 만료 시각을 포함한다.
- 개발용 액세스 토큰은 subject·roles를 포함하고 `exp` claim은 포함하지 않는다.

### core-api 발급 컴포넌트

- 활성 회원 UUID로 요청하면 회원의 현재 역할을 사용해 만료 없는 액세스 토큰을 한 번 발급한다.
- 회원 조회 실패 시 토큰 발급을 시도하지 않고 E1006 원인이 그대로 전파된다.

### core-api HTTP·컨텍스트

- 개발 세션 발급 성공 응답의 `data.accessToken`에 토큰을 싣고 `Set-Cookie` 헤더는 만들지 않는다.
- 없는·탈퇴한 회원은 404 E1006, 잘못된 UUID 요청은 400 E400을 유지한다.
- local·local-dev·dev에서만 발급 빈과 컨트롤러가 등록되고 staging·live에는 등록되지 않는다.
- 만료 없는 액세스 토큰을 Bearer 헤더로 보내면 인증이 필요한 API에 접근할 수 있다.
- 만료된 기존 쿠키가 함께 있어도 개발 세션 발급 경로는 인증 필터에서 차단되지 않는다.

## API 문서·외부 소비자 영향

- 같은 `POST /v1/auth/dev-sessions`의 성공 응답이 비호환 변경된다.
  - 제거: 액세스·리프레시 `Set-Cookie` 헤더
  - 추가: `data.accessToken: string`
- 로컬·dev 환경의 프론트엔드, Swagger·수동 API 테스트, 환경 변수 기반 개발 스크립트가 소비자다. 앞으로 응답의 `data.accessToken`을 `Authorization: Bearer ...`에 사용해야 한다.
- staging·live에는 엔드포인트가 등록되지 않으므로 운영 API 소비자 영향은 없다.
- REST Docs 스니펫과 OpenAPI 산출물은 테스트로 재생성해 응답 필드와 헤더 제거를 검증한다.

## 영향 범위와 제외 범위

- DB·Flyway·엔티티 변경 없음
- Google OAuth 성공 처리, refresh/logout API, 운영 액세스 토큰 30분 TTL 변경 없음
- SecurityConfig의 공개 경로·Bearer 해석 정책 변경 없음
- 토큰 폐기·블랙리스트·개발 환경 키 관리 정책 추가는 범위 밖

## 검증 계획

1. 변경 모듈 단위 테스트와 REST Docs 테스트
2. 무기한 Bearer 인증 컨텍스트 테스트
3. `./gradlew test ktlintCheck`
4. `./gradlew :core:core-api:openapi3`로 성공 응답 스키마와 문서 생성 확인
