package io.plady.moimyeon.admin.config

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.admin.domain.AdminProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.web.context.request.NativeWebRequest
import java.security.Principal
import java.util.UUID

class AdminProviderArgumentResolverTest {
    private val resolver = AdminProviderArgumentResolver()
    private val parameter = mockk<MethodParameter>()

    @Test
    fun `인증된 JWT subject를 관리자 회원 식별자로 전달한다`() {
        val memberId = UUID.randomUUID()
        val webRequest = mockk<NativeWebRequest>()
        every { webRequest.userPrincipal } returns Principal { memberId.toString() }

        val adminProvider = resolver.resolveArgument(parameter, null, webRequest, null)

        assertThat(adminProvider).isEqualTo(AdminProvider(memberId))
    }

    @Test
    fun `인증 주체가 없으면 관리자 정보를 만들지 않는다`() {
        val webRequest = mockk<NativeWebRequest>()
        every { webRequest.userPrincipal } returns null

        assertThatThrownBy { resolver.resolveArgument(parameter, null, webRequest, null) }
            .isInstanceOf(IllegalStateException::class.java)
    }
}
