package io.plady.moimyeon.admin.config

import io.plady.moimyeon.admin.domain.AdminProvider
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.UUID

class AdminProviderArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.parameterType == AdminProvider::class.java
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): AdminProvider {
        val principal = checkNotNull(webRequest.userPrincipal) { "인증된 관리자만 AdminProvider를 사용할 수 있습니다." }
        return AdminProvider(memberId = UUID.fromString(principal.name))
    }
}
