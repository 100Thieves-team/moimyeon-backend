package io.plady.moimyeon.core.domain.session

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.RefreshTokenEntity
import io.plady.moimyeon.storage.db.core.RefreshTokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class SessionFinderTest {
    private val refreshTokenRepository = mockk<RefreshTokenRepository>()
    private val sessionFinder = SessionFinder(refreshTokenRepository)

    private val memberId = UUID.randomUUID()

    @Test
    fun `유효한 세션이면 memberId 를 반환한다`() {
        // given
        val raw = "raw-credential"
        every { refreshTokenRepository.findByTokenHash(RefreshTokenGenerator.hash(raw)) } returns
            RefreshTokenEntity(RefreshTokenGenerator.hash(raw), memberId, LocalDateTime.now().plusDays(1))

        // when
        val result = sessionFinder.getMemberId(raw)

        // then
        assertThat(result).isEqualTo(memberId)
    }

    @Test
    fun `존재하지 않는 세션이면 INVALID_SESSION`() {
        // given
        every { refreshTokenRepository.findByTokenHash(any()) } returns null

        // when & then
        assertThatThrownBy { sessionFinder.getMemberId("nope") }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.INVALID_SESSION)
            }
    }

    @Test
    fun `만료된 세션이면 INVALID_SESSION`() {
        // given
        val raw = "expired"
        every { refreshTokenRepository.findByTokenHash(RefreshTokenGenerator.hash(raw)) } returns
            RefreshTokenEntity(RefreshTokenGenerator.hash(raw), memberId, LocalDateTime.now().minusDays(1))

        // when & then
        assertThatThrownBy { sessionFinder.getMemberId(raw) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.INVALID_SESSION)
            }
    }

    @Test
    fun `종료(revoke)된 세션이면 INVALID_SESSION`() {
        // given
        val raw = "revoked"
        val entity = RefreshTokenEntity(RefreshTokenGenerator.hash(raw), memberId, LocalDateTime.now().plusDays(1))
        entity.revoke(LocalDateTime.now())
        every { refreshTokenRepository.findByTokenHash(RefreshTokenGenerator.hash(raw)) } returns entity

        // when & then
        assertThatThrownBy { sessionFinder.getMemberId(raw) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.INVALID_SESSION)
            }
    }
}
