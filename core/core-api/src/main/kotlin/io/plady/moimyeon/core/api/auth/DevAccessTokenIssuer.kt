package io.plady.moimyeon.core.api.auth

import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.security.auth.JwtTokenProvider
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@Profile("(local | local-dev | dev) & !staging & !live")
class DevAccessTokenIssuer(
    private val memberFinder: MemberFinder,
    private val jwtTokenProvider: JwtTokenProvider,
) {
    fun issue(memberId: UUID): String {
        val member = memberFinder.getById(memberId)
        return jwtTokenProvider.issueWithoutExpiration(member.id, member.role)
    }
}
