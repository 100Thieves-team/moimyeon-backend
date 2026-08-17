package io.plady.moimyeon.core.api.auth

import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.security.auth.AuthCookieFactory
import io.plady.moimyeon.security.auth.JwtTokenProvider
import io.plady.moimyeon.security.auth.SessionIssuer
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@Profile("(local | local-dev | dev) & !live")
class DevSessionCookieIssuer(
    private val memberFinder: MemberFinder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val sessionIssuer: SessionIssuer,
    private val authCookieFactory: AuthCookieFactory,
) {
    fun issue(memberId: UUID): List<ResponseCookie> {
        val member = memberFinder.getById(memberId)
        val accessToken = jwtTokenProvider.issue(member.id, member.role)
        val session = sessionIssuer.open(member.id)

        return listOf(
            authCookieFactory.createAccess(accessToken),
            authCookieFactory.createRefresh(session),
        )
    }
}
