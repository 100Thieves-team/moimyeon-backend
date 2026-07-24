package io.plady.moimyeon.core.domain.session

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.RefreshTokenEntity
import io.plady.moimyeon.storage.db.core.RefreshTokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class SessionManagerTest {
    private val refreshTokenRepository = mockk<RefreshTokenRepository>()
    private val sessionProperties = SessionProperties(ttlSeconds = 1_209_600)
    private val sessionManager = SessionManager(refreshTokenRepository, sessionProperties)

    private val memberId = UUID.randomUUID()

    @Test
    fun `세션을 열면 원문 크리덴셜을 반환하고 저장은 해시로 한다`() {
        // given
        val saved = slot<RefreshTokenEntity>()
        every { refreshTokenRepository.save(capture(saved)) } answers { saved.captured }

        // when
        val issued = sessionManager.open(memberId)

        // then
        assertThat(issued.credential).isNotBlank()
        assertThat(saved.captured.tokenHash).isEqualTo(RefreshTokenGenerator.hash(issued.credential))
        assertThat(saved.captured.tokenHash).isNotEqualTo(issued.credential) // 원문이 그대로 저장되지 않는다
        assertThat(saved.captured.memberId).isEqualTo(memberId)
    }

    @Test
    fun `유효한 세션이면 memberId 를 반환한다`() {
        // given
        val raw = "raw-credential"
        every { refreshTokenRepository.findByTokenHash(RefreshTokenGenerator.hash(raw)) } returns
            RefreshTokenEntity(RefreshTokenGenerator.hash(raw), memberId, LocalDateTime.now().plusDays(1))

        // when
        val result = sessionManager.resolveMemberId(raw)

        // then
        assertThat(result).isEqualTo(memberId)
    }

    @Test
    fun `존재하지 않는 세션이면 INVALID_SESSION`() {
        // given
        every { refreshTokenRepository.findByTokenHash(any()) } returns null

        // when & then
        assertThatThrownBy { sessionManager.resolveMemberId("nope") }
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
        assertThatThrownBy { sessionManager.resolveMemberId(raw) }
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
        assertThatThrownBy { sessionManager.resolveMemberId(raw) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.INVALID_SESSION)
            }
    }

    @Test
    fun `로그아웃하면 세션이 종료된다`() {
        // given
        val raw = "to-revoke"
        val entity = RefreshTokenEntity(RefreshTokenGenerator.hash(raw), memberId, LocalDateTime.now().plusDays(1))
        every { refreshTokenRepository.findByTokenHash(RefreshTokenGenerator.hash(raw)) } returns entity

        // when
        sessionManager.revoke(raw)

        // then
        assertThat(entity.revokedAt).isNotNull()
    }

    @Test
    fun `없는 세션 로그아웃도 예외 없이 통과한다(멱등)`() {
        // given
        every { refreshTokenRepository.findByTokenHash(any()) } returns null

        // when & then
        assertThatCode { sessionManager.revoke("nope") }.doesNotThrowAnyException()
    }
}
