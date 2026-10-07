package io.plady.moimyeon.security.auth

import java.util.UUID

interface SessionIssuer {
    fun open(memberId: UUID): IssuedSession
}
