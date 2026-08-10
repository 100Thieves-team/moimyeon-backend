package io.plady.moimyeon.security.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.plady.moimyeon.core.enums.MemberRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import java.time.Instant
import java.util.UUID

class JwtTokenProviderTest {
    @Test
    fun `회원 역할을 JWT roles claim에 기록한다`() {
        val encoder = mockk<JwtEncoder>()
        val parameters = slot<JwtEncoderParameters>()
        every { encoder.encode(capture(parameters)) } returns token()
        val provider = JwtTokenProvider(encoder)
        val memberId = UUID.randomUUID()

        provider.issue(memberId, MemberRole.ADMIN)

        assertThat(parameters.captured.claims.subject).isEqualTo(memberId.toString())
        assertThat(parameters.captured.claims.getClaim<List<String>>("roles"))
            .containsExactly(MemberRole.ADMIN.name)
    }

    private fun token(): Jwt = Jwt.withTokenValue("token")
        .header("alg", "HS256")
        .subject("member")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .build()
}
