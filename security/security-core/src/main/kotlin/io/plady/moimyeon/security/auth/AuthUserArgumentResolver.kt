package io.plady.moimyeon.security.auth

import org.springframework.core.MethodParameter
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

// SecurityContext 접근은 이 리졸버 한 곳으로 격리한다. 컨트롤러·서비스는 AuthUser 만 본다.
// 계약: Authentication.name = 내부 userId 문자열 (provider ID 는 인증 어댑터에서 끝난다)
class AuthUserArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.parameterType == AuthUser::class.java
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): AuthUser {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw AuthenticationCredentialsNotFoundException("인증 정보가 없습니다.")
        val userId = authentication.name?.toLongOrNull()
            ?: throw AuthenticationCredentialsNotFoundException("인증 주체를 해석할 수 없습니다.")
        return AuthUser(id = userId)
    }
}
