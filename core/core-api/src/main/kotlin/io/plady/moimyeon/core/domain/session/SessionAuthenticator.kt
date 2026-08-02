package io.plady.moimyeon.core.domain.session

import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.RefreshTokenRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Component
class SessionAuthenticator(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val memberFinder: MemberFinder,
) {
    @Transactional(readOnly = true)
    fun authenticate(credential: SessionCredential, authenticatedAt: LocalDateTime): UUID {
        val session = requireFound(
            refreshTokenRepository.findByTokenHash(credential.hash()),
            CoreErrorType.INVALID_SESSION,
        )
        requireBusiness(session.isActive(authenticatedAt), CoreErrorType.INVALID_SESSION)
        requireBusiness(memberFinder.existsById(session.memberId), CoreErrorType.INVALID_SESSION)
        return session.memberId
    }
}
