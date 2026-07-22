package io.plady.moimyeon.security.auth

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
    fun issue(memberId: UUID): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject(memberId.toString()) // sub = 내부 memberId(UUID)
            .issuedAt(now)
            .expiresAt(now.plus(TOKEN_TTL_HOURS, ChronoUnit.HOURS))
            .build()
        return jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .tokenValue
    }

    companion object {
        private const val TOKEN_TTL_HOURS = 1L
    }
}
