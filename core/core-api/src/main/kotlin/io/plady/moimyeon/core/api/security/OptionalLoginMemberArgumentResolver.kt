package io.plady.moimyeon.core.api.security

import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.UUID

// 계약: userPrincipal.name = 회원 UUID 문자열 (security 모듈의 인증 필터가 보장)
//
// LoginMemberArgumentResolver 와 나눠 두는 이유는 실패의 의미가 반대이기 때문이다.
// 그쪽은 principal 이 없으면 401 이고, 여기는 비로그인이다. 한 리졸버가 두 계약을 가지면
// 어느 쪽으로 해석되는지가 파라미터 선언이 아니라 리졸버 내부 분기에 숨는다.
//
// ⚠️ 이 리졸버가 null 을 돌려주는 것은 "토큰이 없다"까지다. 만료·위조 토큰은 여기 오기 전에
// 필터 체인이 401 로 끊는다(permitAll 경로여도 그렇다) — 클라이언트가 재발급 후 다시 부른다.
@Component
class OptionalLoginMemberArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(OptionalLoginMember::class.java) &&
            parameter.parameterType == CurrentMember::class.java
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): CurrentMember? {
        return webRequest.userPrincipal?.name?.toUuidOrNull()?.let { CurrentMember(id = it) }
    }

    // 깨진 principal 은 비로그인으로 본다. 401 을 던지면 공개 조회가 막히고,
    // 값을 그대로 쓰면 회원이 아닌 문자열이 회원 식별자로 흘러든다.
    private fun String.toUuidOrNull(): UUID? = try {
        UUID.fromString(this)
    } catch (e: IllegalArgumentException) {
        null
    }
}
