package io.plady.moimyeon.core.domain.session

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.RefreshTokenRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

@Component
class SessionFinder(
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    fun getMemberId(rawCredential: String): UUID {
        val entity = requireFound(
            refreshTokenRepository.findByTokenHash(RefreshTokenGenerator.hash(rawCredential)),
            CoreErrorType.INVALID_SESSION,
        )
        requireBusiness(entity.isActive(LocalDateTime.now()), CoreErrorType.INVALID_SESSION)
        return entity.memberId
    }
}
