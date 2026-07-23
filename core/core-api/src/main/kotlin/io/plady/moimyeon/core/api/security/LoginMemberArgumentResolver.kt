package io.plady.moimyeon.core.api.security

import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.error.ErrorType
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.UUID

// 계약: userPrincipal.name = 회원 UUID 문자열 (security 모듈의 인증 필터가 보장)
@Component
class LoginMemberArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(LoginMember::class.java) &&
            parameter.parameterType == CurrentMember::class.java
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): CurrentMember {
        val memberId = webRequest.userPrincipal?.name
        return CurrentMember(id = memberId.toUuid())
    }

    private fun String?.toUuid(): UUID {
        if (this == null) {
            throw CoreException(ErrorType.AUTHENTICATION_REQUIRED)
        }

        return try {
            UUID.fromString(this)
        } catch (e: IllegalArgumentException) {
            throw CoreException(ErrorType.AUTHENTICATION_REQUIRED)
        }
    }
}
