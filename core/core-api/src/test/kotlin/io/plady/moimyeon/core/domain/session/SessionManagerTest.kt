package io.plady.moimyeon.core.domain.session

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.plady.moimyeon.storage.db.core.RefreshTokenEntity
import io.plady.moimyeon.storage.db.core.RefreshTokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
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
