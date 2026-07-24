package io.plady.moimyeon.core.domain.session

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.MemberManager
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional

@Transactional
class SessionServiceIT(
    private val sessionService: SessionService,
    private val sessionManager: SessionManager,
    private val memberManager: MemberManager,
) : ContextTest() {
    private val provider = SocialLoginProvider.GOOGLE

    @Test
    fun `유효한 세션 크리덴셜로 재발급하면 그 회원의 memberId 를 반환한다`() {
        // given
        val memberId = memberManager.append(provider, "google-sub-1", Email("user@example.com"))
        val session = sessionManager.open(memberId)

        // when
        val result = sessionService.refreshAccess(session.credential)

        // then
        assertThat(result).isEqualTo(memberId)
    }

    @Test
    fun `로그아웃으로 종료된 세션 크리덴셜로는 재발급할 수 없다`() {
        // given
        val memberId = memberManager.append(provider, "google-sub-2", Email("user@example.com"))
        val session = sessionManager.open(memberId)

        // when
        sessionService.logout(session.credential)

        // then
        assertThatThrownBy { sessionService.refreshAccess(session.credential) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.INVALID_SESSION)
            }
    }
}
