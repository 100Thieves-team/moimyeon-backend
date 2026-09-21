package io.plady.moimyeon.core.domain.session

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class SessionService(
    private val sessionAuthenticator: SessionAuthenticator,
    private val sessionManager: SessionManager,
) {
    fun open(memberId: UUID): Session {
        log.debug { "session.open memberId=$memberId" }
        return sessionManager.open(memberId, LocalDateTime.now())
    }

    fun authenticate(rawCredential: String): UUID {
        log.debug { "session.authenticate" }
        return sessionAuthenticator.authenticate(SessionCredential.from(rawCredential), LocalDateTime.now())
    }

    fun logout(rawCredential: String) {
        log.debug { "session.logout" }
        sessionManager.close(SessionCredential.from(rawCredential), LocalDateTime.now())
    }
}
