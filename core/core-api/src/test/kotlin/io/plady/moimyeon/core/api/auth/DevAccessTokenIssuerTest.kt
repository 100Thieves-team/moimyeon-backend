package io.plady.moimyeon.core.api.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.member.Member
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.enums.MemberRole
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.security.auth.JwtTokenProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class DevAccessTokenIssuerTest {
    private val memberFinder = mockk<MemberFinder>()
    private val jwtTokenProvider = mockk<JwtTokenProvider>()
    private val issuer = DevAccessTokenIssuer(memberFinder, jwtTokenProvider)

    @Test
    fun `활성 회원의 현재 권한으로 만료 없는 액세스 토큰을 발급한다`() {
        val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val member = mockk<Member>()
        every { memberFinder.getById(memberId) } returns member
        every { member.id } returns memberId
        every { member.role } returns MemberRole.ADMIN
        every { jwtTokenProvider.issueWithoutExpiration(memberId, MemberRole.ADMIN) } returns "access-token"

        val issuedToken = issuer.issue(memberId)

        assertThat(issuedToken).isEqualTo("access-token")
        verifyOrder {
            memberFinder.getById(memberId)
            jwtTokenProvider.issueWithoutExpiration(memberId, MemberRole.ADMIN)
        }
    }

    @Test
    fun `없는 회원이면 E1006을 전파하고 액세스 토큰을 발급하지 않는다`() {
        val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val failure = CoreException(CoreErrorType.MEMBER_NOT_FOUND)
        every { memberFinder.getById(memberId) } throws failure

        assertThatThrownBy { issuer.issue(memberId) }
            .isSameAs(failure)

        verify(exactly = 0) { jwtTokenProvider.issueWithoutExpiration(any(), any()) }
    }
}
