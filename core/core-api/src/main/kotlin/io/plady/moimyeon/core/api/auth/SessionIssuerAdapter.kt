package io.plady.moimyeon.core.api.auth

import io.plady.moimyeon.core.domain.session.SessionService
import io.plady.moimyeon.security.auth.IssuedSession
import io.plady.moimyeon.security.auth.SessionIssuer
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class SessionIssuerAdapter(
    private val sessionService: SessionService,
) : SessionIssuer {
    override fun open(memberId: UUID): IssuedSession {
        val session = sessionService.open(memberId)
        return IssuedSession(
            credential = session.credential.value,
            expiresAt = session.expiresAt,
        )
    }
}
