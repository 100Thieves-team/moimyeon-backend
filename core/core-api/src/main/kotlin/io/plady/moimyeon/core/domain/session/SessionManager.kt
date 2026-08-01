package io.plady.moimyeon.core.domain.session

import io.plady.moimyeon.storage.db.core.RefreshTokenEntity
import io.plady.moimyeon.storage.db.core.RefreshTokenRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Component
class SessionManager(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val sessionProperties: SessionProperties,
) {
    @Transactional
    fun open(memberId: UUID, openedAt: LocalDateTime): Session {
        val credential = SessionCredential.issue()
        val expiresAt = openedAt.plusSeconds(sessionProperties.ttlSeconds)
        refreshTokenRepository.save(
            RefreshTokenEntity(
                tokenHash = credential.hash(),
                memberId = memberId,
                expiresAt = expiresAt,
            ),
        )
        return Session(credential = credential, expiresAt = expiresAt)
    }

    @Transactional
    fun close(credential: SessionCredential, closedAt: LocalDateTime) {
        refreshTokenRepository.findByTokenHash(credential.hash())?.revoke(closedAt)
    }
}
