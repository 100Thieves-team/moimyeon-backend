package io.plady.moimyeon.security.auth

import io.plady.moimyeon.core.enums.MemberRole
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Component
class JwtTokenProvider(
    private val jwtEncoder: JwtEncoder,
) {
    fun issue(memberId: UUID, role: MemberRole): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject(memberId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(TOKEN_TTL_MINUTES, ChronoUnit.MINUTES))
            .claim(ROLES_CLAIM, listOf(role.name))
            .build()
        return jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .tokenValue
    }

    companion object {
        private const val TOKEN_TTL_MINUTES = 30L
        const val ROLES_CLAIM = "roles"
    }
}
