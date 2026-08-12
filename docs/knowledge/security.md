# 보안 지식

인증·인가·비밀값을 다룰 때 이 저장소에서 알고 있어야 하는 것.
규칙은 [`../conventions/auth.md`](../conventions/auth.md)에 있고, 여기는 그 규칙만 봐서는 안 드러나는 함정이다.

---

## 이 스택에서 걸리는 것

**Kotlin 클래스는 기본 `final`이다.**
Spring이 프록시로 구현하는 보안 애노테이션(`@PreAuthorize` 등)은 `open`이 아닌 메서드에서
조용히 무시될 수 있다. 애노테이션을 붙였는데 안 걸린다면 이걸 먼저 의심한다.
all-open 플러그인이 그 애노테이션을 커버하는지는 `build.gradle.kts`에서 확인한다.

**인증 주체를 받는 방식은 하나로 정해져 있다.**
[`../conventions/auth.md`](../conventions/auth.md)의 "컨트롤러에서 인증 주체 받기" 참고.
컨트롤러에서 토큰을 직접 파싱하거나 자체 검사를 넣지 않는다. 그게 오히려 사고 지점이 된다.
반대로 정해진 방식을 쓰는 경로에 인가 검사가 없어 보인다면, 없는 게 아니라
어디서 적용되는지 모르는 것일 수 있다 — `security-core`부터 확인한다.

**인증 기술은 `security-core` 격벽 안에 있다.**
`core`가 인증 라이브러리에 직접 의존하면 아키텍처 위반이자 공격면 확대다.
([`../conventions/modules.md`](../conventions/modules.md))

**로그인은 Google OAuth다.**
`GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_SECRET`, `JWT_SECRET`이 환경변수로 주입된다
(`.github/workflows/ci.yml` 참고). 이 값들이 로그·에러 응답·테스트 픽스처로 흘러가는
경로를 만들지 않는다.

## 위험도 감각

무엇이 얼마나 급한 문제인지에 대한 팀의 기준.

| | |
| --- | --- |
| 즉시 막아야 함 | 인증 우회, 다른 사용자 데이터 접근, 비밀값이 로그·응답·저장소에 노출 |
| 머지 전에 고쳐야 함 | 인가 검사 누락, 외부 입력이 검증 없이 쿼리·경로·명령으로 흘러감 |
| 알고 넘어가도 됨 | 심층 방어 부재 — 지금은 다른 계층이 막지만 그게 사라지면 뚫리는 것 |

## 우리가 겪은 것

<!--
장애·버그가 날 때마다 한 줄. 날짜 / 무엇이 / 어떤 조건에서 / 원인 / 어디를 보면 되는지.
-->

- 2026-08-12: Google OAuth 동의 후 dev API 루트로 이동하고 세션 쿠키가 발급되지 않았다.
  원인: `SecurityConfig`의 모든 생성자 인자가 nullable 기본값이라 Spring이 무인자 생성자로 조립했고
  커스텀 성공 핸들러가 필터 체인에서 빠졌다. 재발 방지: 필수 인증 의존성과 OAuth 필터 체인 컨텍스트 테스트.
- 2026-08-12: OAuth 성공 핸들러만 지정하면 Google 거절·state 오류는 Spring 기본 `/login?error`로 가고,
  인증 뒤 회원·세션 처리 예외는 백엔드 500으로 남는다. 성공·실패 핸들러를 함께 조립하고 성공 처리 내부
  예외도 부분 쿠키 없이 고정된 프론트 실패 리다이렉트로 닫아야 한다.
- 2026-08-13: Google OAuth 인증 뒤 회원 역할을 읽는 과정에서 `LazyInitializationException`이 발생했다.
  원인: Repository 트랜잭션 종료 후 `MemberMapper`가 LAZY `socialAccounts`를 읽었다. 재발 방지:
  OSIV와 기본 LAZY는 유지하고, 완전한 회원 조립 조회만 이름에 연관을 드러낸 JPQL fetch join을 사용한다.
- 2026-08-13: 공개 ALB에 비밀파일·PHP 경로 약 3,900건을 대입하는 자동 스캔이 유입됐다.
  노출 파일이나 5xx는 없었지만 ALB 액세스 로그와 WAF가 없어 발신자 추적·선제 차단을 못 했다.
  재발 방지: ALB 액세스 로그, WAF IP rate limit·관리형 규칙 관찰, 공개 actuator 최소화.
