package io.plady.moimyeon.core.api.security

// 공개 조회에서 "로그인했으면 회원, 아니면 없음"을 받는다. 필수 해석은 @LoginMember 가 그대로 갖는다.
// 파라미터를 CurrentMember? 로 선언해 비로그인 경로가 컨트롤러 시그니처에 드러나게 한다.
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class OptionalLoginMember
