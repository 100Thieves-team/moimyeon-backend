package io.plady.moimyeon.core.domain.session

import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

@Service
class SessionService(
    private val sessionAuthenticator: SessionAuthenticator,
    private val sessionManager: SessionManager,
) {
    fun open(memberId: UUID): Session {
        return sessionManager.open(memberId, LocalDateTime.now())
    }

    fun authenticate(rawCredential: String): UUID {
        return sessionAuthenticator.authenticate(SessionCredential.from(rawCredential), LocalDateTime.now())
    }

    fun logout(rawCredential: String) {
        sessionManager.close(SessionCredential.from(rawCredential), LocalDateTime.now())
    }
}
