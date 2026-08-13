package io.plady.moimyeon.core.api.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.ServletWebRequest
import java.security.Principal
import java.util.UUID

// 공개 조회(탐색 목록·룸 상세)는 로그인 여부로 응답을 나눈다. 필수 해석(@LoginMember)은 그대로 두고
// 이쪽만 "로그인했으면 회원, 아니면 없음"으로 답한다.
class OptionalLoginMemberArgumentResolverTest {
    private val resolver = OptionalLoginMemberArgumentResolver()

    @Test
    fun `어노테이션과 CurrentMember 타입을 모두 갖춘 파라미터만 지원한다`() {
        assertThat(resolver.supportsParameter(parameter("annotated", CurrentMember::class.java))).isTrue()
        assertThat(resolver.supportsParameter(parameter("plainType", CurrentMember::class.java))).isFalse()
        assertThat(resolver.supportsParameter(parameter("wrongType", UUID::class.java))).isFalse()
    }

    // 필수 해석과 한 파라미터를 두고 다투면 어느 쪽이 이기는지가 등록 순서에 달리게 된다.
    @Test
    fun `필수 해석용 파라미터는 지원하지 않는다`() {
        assertThat(resolver.supportsParameter(parameter("required", CurrentMember::class.java))).isFalse()
    }

    @Test
    fun `요청 principal name 을 회원 UUID 로 변환한다`() {
        val request = MockHttpServletRequest().apply {
            userPrincipal = Principal { MEMBER_ID.toString() }
        }

        val currentMember = resolver.resolveArgument(
            parameter("annotated", CurrentMember::class.java),
            null,
            ServletWebRequest(request),
            null,
        )

        assertThat(currentMember?.id).isEqualTo(MEMBER_ID)
    }

    @Test
    fun `principal 이 없으면 null 을 돌려준다`() {
        val currentMember = resolver.resolveArgument(
            parameter("annotated", CurrentMember::class.java),
            null,
            ServletWebRequest(MockHttpServletRequest()),
            null,
        )

        assertThat(currentMember).isNull()
    }

    // 깨진 principal 에 401 을 던지면 비로그인 탐색이 막히고, 그렇다고 값을 그대로 쓰면
    // 회원이 아닌 문자열이 회원 식별자로 흘러든다. null 이 유일하게 안전한 답이다.
    @Test
    fun `principal name 이 UUID 가 아니면 null 을 돌려준다`() {
        val request = MockHttpServletRequest().apply {
            userPrincipal = Principal { "google-sub-123" }
        }

        val currentMember = resolver.resolveArgument(
            parameter("annotated", CurrentMember::class.java),
            null,
            ServletWebRequest(request),
            null,
        )

        assertThat(currentMember).isNull()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    private fun annotated(
        @OptionalLoginMember currentMember: CurrentMember?,
    ) = Unit

    @Suppress("unused", "UNUSED_PARAMETER")
    private fun plainType(currentMember: CurrentMember?) = Unit

    @Suppress("unused", "UNUSED_PARAMETER")
    private fun wrongType(
        @OptionalLoginMember memberId: UUID?,
    ) = Unit

    @Suppress("unused", "UNUSED_PARAMETER")
    private fun required(
        @LoginMember currentMember: CurrentMember,
    ) = Unit

    private fun parameter(methodName: String, parameterType: Class<*>): MethodParameter {
        return MethodParameter(javaClass.getDeclaredMethod(methodName, parameterType), 0)
    }

    private companion object {
        val MEMBER_ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    }
}
