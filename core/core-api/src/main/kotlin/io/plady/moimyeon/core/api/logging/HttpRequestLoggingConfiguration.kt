package io.plady.moimyeon.core.api.logging

import io.plady.moimyeon.support.logging.RequestLogWriter
import jakarta.servlet.DispatcherType
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.servlet.handler.MappedInterceptor

@Configuration(proxyBeanMethods = false)
class HttpRequestLoggingConfiguration {
    @Bean
    fun httpRequestLoggingFilter(): FilterRegistrationBean<HttpRequestLoggingFilter> = FilterRegistrationBean(HttpRequestLoggingFilter()).apply {
        // Boot's observation filter is HIGHEST_PRECEDENCE + 1; Security runs later.
        order = Ordered.HIGHEST_PRECEDENCE + 2
        setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR)
        setAsyncSupported(true)
    }

    @Bean
    fun httpRequestLogCompletionListener(writer: RequestLogWriter): ServletListenerRegistrationBean<HttpRequestLogCompletionListener> = ServletListenerRegistrationBean(HttpRequestLogCompletionListener(writer))

    @Bean
    fun requestLogRouteInterceptor(): MappedInterceptor = MappedInterceptor(null, RequestLogRouteInterceptor())
}
