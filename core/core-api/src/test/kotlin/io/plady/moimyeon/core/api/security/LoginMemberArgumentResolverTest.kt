package io.plady.moimyeon.core.api.security

import io.plady.moimyeon.core.support.error.CoreApiErrorType
import io.plady.moimyeon.core.support.error.CoreApiException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.ServletWebRequest
import java.security.Principal
import java.util.UUID

class LoginMemberArgumentResolverTest {
    private val resolver = LoginMemberArgumentResolver()

    @Test
    fun `어노테이션과 CurrentMember 타입을 모두 갖춘 파라미터만 지원한다`() {
        assertThat(resolver.supportsParameter(parameter("annotated", CurrentMember::class.java))).isTrue()
        assertThat(resolver.supportsParameter(parameter("plainType", CurrentMember::class.java))).isFalse()
        assertThat(resolver.supportsParameter(parameter("wrongType", UUID::class.java))).isFalse()
    }

    @Test
    fun `요청 principal name 을 회원 UUID 로 변환한다`() {
        val request = MockHttpServletRequest().apply {
            userPrincipal = Principal { MEMBER_ID.toString() }
        }

        val currentMember = resolver.resolveArgument(parameter("annotated", CurrentMember::class.java), null, ServletWebRequest(request), null)

        assertThat(currentMember.id).isEqualTo(MEMBER_ID)
    }

    @Test
    fun `principal 이 없으면 인증 오류를 던진다`() {
        assertThatThrownBy {
            resolver.resolveArgument(parameter("annotated", CurrentMember::class.java), null, ServletWebRequest(MockHttpServletRequest()), null)
        }
            .isInstanceOf(CoreApiException::class.java)
            .extracting("errorType")
            .isEqualTo(CoreApiErrorType.AUTHENTICATION_REQUIRED)
    }

    @Test
    fun `principal name 이 UUID 가 아니면 인증 오류를 던진다`() {
        val request = MockHttpServletRequest().apply {
            userPrincipal = Principal { "google-sub-123" }
        }

        assertThatThrownBy {
            resolver.resolveArgument(parameter("annotated", CurrentMember::class.java), null, ServletWebRequest(request), null)
        }
            .isInstanceOf(CoreApiException::class.java)
            .extracting("errorType")
            .isEqualTo(CoreApiErrorType.AUTHENTICATION_REQUIRED)
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    private fun annotated(
        @LoginMember currentMember: CurrentMember,
    ) = Unit

    @Suppress("unused", "UNUSED_PARAMETER")
    private fun plainType(currentMember: CurrentMember) = Unit

    @Suppress("unused", "UNUSED_PARAMETER")
    private fun wrongType(
        @LoginMember memberId: UUID,
    ) = Unit

    private fun parameter(methodName: String, parameterType: Class<*>): MethodParameter {
        return MethodParameter(javaClass.getDeclaredMethod(methodName, parameterType), 0)
    }

    private companion object {
        val MEMBER_ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    }
}
