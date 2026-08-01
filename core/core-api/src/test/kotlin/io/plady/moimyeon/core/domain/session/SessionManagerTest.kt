package io.plady.moimyeon.core.domain.session

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.plady.moimyeon.storage.db.core.RefreshTokenEntity
import io.plady.moimyeon.storage.db.core.RefreshTokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class SessionManagerTest {
    private val refreshTokenRepository = mockk<RefreshTokenRepository>()
    private val sessionProperties = SessionProperties(ttlSeconds = 1_209_600)
    private val sessionManager = SessionManager(refreshTokenRepository, sessionProperties)

    private val memberId = UUID.randomUUID()
    private val openedAt = LocalDateTime.of(2026, 8, 1, 12, 0)

    @Test
    fun `세션을 열면 원문 크리덴셜과 만료 시각을 반환하고 저장은 해시로 한다`() {
        val saved = slot<RefreshTokenEntity>()
        every { refreshTokenRepository.save(capture(saved)) } answers { saved.captured }

        val session = sessionManager.open(memberId, openedAt)

        assertThat(session.credential.value).isNotBlank()
        assertThat(session.expiresAt).isEqualTo(openedAt.plusSeconds(sessionProperties.ttlSeconds))
        assertThat(saved.captured.tokenHash).isEqualTo(session.credential.hash())
        assertThat(saved.captured.tokenHash).isNotEqualTo(session.credential.value)
        assertThat(saved.captured.memberId).isEqualTo(memberId)
        assertThat(saved.captured.expiresAt).isEqualTo(session.expiresAt)
    }

    @Test
    fun `세션을 종료하면 최초 종료 시각을 기록한다`() {
        val credential = SessionCredential.from("to-close")
        val closedAt = openedAt.plusHours(1)
        val entity = RefreshTokenEntity(credential.hash(), memberId, openedAt.plusDays(1))
        every { refreshTokenRepository.findByTokenHash(credential.hash()) } returns entity

        sessionManager.close(credential, closedAt)

        assertThat(entity.revokedAt).isEqualTo(closedAt)
    }

    @Test
    fun `존재하지 않는 세션 종료도 예외 없이 통과한다`() {
        val credential = SessionCredential.from("unknown")
        every { refreshTokenRepository.findByTokenHash(credential.hash()) } returns null

        sessionManager.close(credential, openedAt)
    }

    @Test
    fun `이미 종료된 세션을 다시 종료해도 최초 종료 시각을 보존한다`() {
        val credential = SessionCredential.from("already-closed")
        val firstClosedAt = openedAt.plusHours(1)
        val entity = RefreshTokenEntity(credential.hash(), memberId, openedAt.plusDays(1))
        every { refreshTokenRepository.findByTokenHash(credential.hash()) } returns entity

        sessionManager.close(credential, firstClosedAt)
        sessionManager.close(credential, firstClosedAt.plusHours(1))

        assertThat(entity.revokedAt).isEqualTo(firstClosedAt)
    }
}
