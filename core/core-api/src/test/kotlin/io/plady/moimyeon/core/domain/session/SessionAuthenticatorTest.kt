package io.plady.moimyeon.core.domain.session

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.RefreshTokenEntity
import io.plady.moimyeon.storage.db.core.RefreshTokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class SessionAuthenticatorTest {
    private val refreshTokenRepository = mockk<RefreshTokenRepository>()
    private val memberFinder = mockk<MemberFinder>()
    private val sessionAuthenticator = SessionAuthenticator(refreshTokenRepository, memberFinder)

    private val memberId = UUID.randomUUID()
    private val authenticatedAt = LocalDateTime.of(2026, 8, 1, 12, 0)

    @Test
    fun `유효한 세션이고 탈퇴하지 않은 회원이면 회원 id 를 반환한다`() {
        val credential = SessionCredential.from("valid-credential")
        every { refreshTokenRepository.findByTokenHash(credential.hash()) } returns
            session(memberId, authenticatedAt.plusDays(1))
        every { memberFinder.existsById(memberId) } returns true

        val result = sessionAuthenticator.authenticate(credential, authenticatedAt)

        assertThat(result).isEqualTo(memberId)
    }

    @Test
    fun `존재하지 않는 세션이면 E1104 를 던진다`() {
        val credential = SessionCredential.from("unknown-credential")
        every { refreshTokenRepository.findByTokenHash(credential.hash()) } returns null

        assertInvalidSession { sessionAuthenticator.authenticate(credential, authenticatedAt) }
        verify(exactly = 0) { memberFinder.existsById(any()) }
    }

    @Test
    fun `만료된 세션이면 E1104 를 던진다`() {
        val credential = SessionCredential.from("expired-credential")
        every { refreshTokenRepository.findByTokenHash(credential.hash()) } returns
            session(memberId, authenticatedAt.minusSeconds(1))

        assertInvalidSession { sessionAuthenticator.authenticate(credential, authenticatedAt) }
        verify(exactly = 0) { memberFinder.existsById(any()) }
    }

    @Test
    fun `종료된 세션이면 E1104 를 던진다`() {
        val credential = SessionCredential.from("closed-credential")
        val closedSession = session(memberId, authenticatedAt.plusDays(1))
        closedSession.revoke(authenticatedAt.minusSeconds(1))
        every { refreshTokenRepository.findByTokenHash(credential.hash()) } returns closedSession

        assertInvalidSession { sessionAuthenticator.authenticate(credential, authenticatedAt) }
        verify(exactly = 0) { memberFinder.existsById(any()) }
    }

    @Test
    fun `세션의 회원이 탈퇴했으면 회원 정보를 노출하지 않고 E1104 를 던진다`() {
        val credential = SessionCredential.from("withdrawn-member-credential")
        every { refreshTokenRepository.findByTokenHash(credential.hash()) } returns
            session(memberId, authenticatedAt.plusDays(1))
        every { memberFinder.existsById(memberId) } returns false

        assertInvalidSession { sessionAuthenticator.authenticate(credential, authenticatedAt) }
    }

    private fun session(memberId: UUID, expiresAt: LocalDateTime): RefreshTokenEntity {
        return RefreshTokenEntity(
            tokenHash = "stored-hash",
            memberId = memberId,
            expiresAt = expiresAt,
        )
    }

    private fun assertInvalidSession(block: () -> Unit) {
        assertThatThrownBy(block)
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.INVALID_SESSION)
            }
    }
}
