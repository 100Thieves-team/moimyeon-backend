package io.plady.moimyeon.security.auth

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.crypto.SecretKey

@Component
class RestoreTokenProvider(
    private val jwtEncoder: JwtEncoder,
    jwtSecretKey: SecretKey,
) {
    private val decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey).build().apply {
        setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefault(),
                JwtClaimValidator<String>(PURPOSE_CLAIM) { it == PURPOSE },
            ),
        )
    }

    fun issue(memberId: UUID): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject(memberId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(TTL))
            .claim(PURPOSE_CLAIM, PURPOSE)
            .build()
        return jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .tokenValue
    }

    // 만료·위조·다른 용도의 토큰은 모두 같은 결과(null)다 — 호출자는 하나의 거절 사유로 응답한다.
    fun resolve(token: String): RestoreClaim? = try {
        val jwt = decoder.decode(token)
        RestoreClaim(memberId = UUID.fromString(jwt.subject), issuedAt = checkNotNull(jwt.issuedAt))
    } catch (_: JwtException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        // 로그인 직후 화면에서 바로 확인하는 흐름이라 짧게 둔다. 만료되면 다시 로그인하면 된다(SSOT DEC-015).
        val TTL: Duration = Duration.ofMinutes(10)
        const val PURPOSE_CLAIM = "purpose"
        const val PURPOSE = "member-restore"
    }
}
