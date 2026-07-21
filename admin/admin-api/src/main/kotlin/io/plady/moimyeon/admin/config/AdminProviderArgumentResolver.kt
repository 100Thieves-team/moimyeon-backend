package io.plady.moimyeon.admin.config

import io.plady.moimyeon.admin.domain.AdminProvider
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

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
        // TODO: security 연동 후 인증 컨텍스트에서 실제 어드민 정보를 조회한다
        return AdminProvider(id = 0, name = "anonymous")
    }
}
