package io.plady.moimyeon.core.domain.session

import io.plady.moimyeon.security.auth.IssuedSession
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
    fun open(memberId: UUID): IssuedSession {
        val rawCredential = RefreshTokenGenerator.generate()
        val expiresAt = LocalDateTime.now().plusSeconds(sessionProperties.ttlSeconds)
        refreshTokenRepository.save(
            RefreshTokenEntity(
                tokenHash = RefreshTokenGenerator.hash(rawCredential),
                memberId = memberId,
                expiresAt = expiresAt,
            ),
        )
        return IssuedSession(credential = rawCredential, expiresAt = expiresAt)
    }

    @Transactional
    fun revoke(rawCredential: String) {
        refreshTokenRepository.findByTokenHash(RefreshTokenGenerator.hash(rawCredential))?.revoke(LocalDateTime.now())
    }
}
