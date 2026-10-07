package io.plady.moimyeon.core.api.config

import io.plady.moimyeon.core.api.security.LoginMemberArgumentResolver
import io.plady.moimyeon.core.api.security.OptionalLoginMemberArgumentResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val loginMemberArgumentResolver: LoginMemberArgumentResolver,
    private val optionalLoginMemberArgumentResolver: OptionalLoginMemberArgumentResolver,
) : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        // 둘은 애노테이션으로 갈리므로 등록 순서에 의존하지 않는다.
        resolvers.add(loginMemberArgumentResolver)
        resolvers.add(optionalLoginMemberArgumentResolver)
    }
}
