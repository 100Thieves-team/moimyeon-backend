package io.plady.moimyeon.core.api.auth

import io.plady.moimyeon.core.domain.session.SessionManager
import io.plady.moimyeon.security.auth.IssuedSession
import io.plady.moimyeon.security.auth.SessionIssuer
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class SessionIssuerAdapter(
    private val sessionManager: SessionManager,
) : SessionIssuer {
    override fun open(memberId: UUID): IssuedSession = sessionManager.open(memberId)
}
