package io.plady.moimyeon.security.auth

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class AuthUserArgumentResolverTest {
    private val resolver = AuthUserArgumentResolver()

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `AuthUser_타입_파라미터만_지원한다`() {
        val authUserParameter = mockk<MethodParameter> { every { parameterType } returns AuthUser::class.java }
        val otherParameter = mockk<MethodParameter> { every { parameterType } returns String::class.java }

        assertThat(resolver.supportsParameter(authUserParameter)).isTrue()
        assertThat(resolver.supportsParameter(otherParameter)).isFalse()
    }

    @Test
    fun `인증된_사용자의_id를_AuthUser로_변환한다`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "42",
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER")),
        )

        val result = resolver.resolveArgument(mockk(), null, mockk(), null)

        assertThat(result).isEqualTo(AuthUser(id = 42))
    }

    @Test
    fun `인증_정보가_없으면_예외가_발생한다`() {
        assertThatThrownBy { resolver.resolveArgument(mockk(), null, mockk(), null) }
            .isInstanceOf(AuthenticationCredentialsNotFoundException::class.java)
    }

    @Test
    fun `인증_주체가_userId_형식이_아니면_예외가_발생한다`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "anonymousUser",
            null,
            listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")),
        )

        assertThatThrownBy { resolver.resolveArgument(mockk(), null, mockk(), null) }
            .isInstanceOf(AuthenticationCredentialsNotFoundException::class.java)
    }
}
