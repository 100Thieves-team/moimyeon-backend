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
import java.time.Duration
import java.time.Instant
import java.util.UUID

class JwtTokenProviderTest {
    @Test
    fun `일반 액세스 토큰은 회원 정보와 30분 만료 시각을 기록한다`() {
        val encoder = mockk<JwtEncoder>()
        val parameters = slot<JwtEncoderParameters>()
        every { encoder.encode(capture(parameters)) } returns token()
        val provider = JwtTokenProvider(encoder)
        val memberId = UUID.randomUUID()

        provider.issue(memberId, MemberRole.ADMIN)

        assertThat(parameters.captured.claims.subject).isEqualTo(memberId.toString())
        assertThat(parameters.captured.claims.getClaim<List<String>>("roles"))
            .containsExactly(MemberRole.ADMIN.name)
        assertThat(
            Duration.between(
                parameters.captured.claims.issuedAt,
                parameters.captured.claims.expiresAt,
            ),
        ).isEqualTo(Duration.ofMinutes(30))
    }

    @Test
    fun `개발용 액세스 토큰은 회원 정보를 기록하고 만료 시각은 기록하지 않는다`() {
        val encoder = mockk<JwtEncoder>()
        val parameters = slot<JwtEncoderParameters>()
        every { encoder.encode(capture(parameters)) } returns token()
        val provider = JwtTokenProvider(encoder)
        val memberId = UUID.randomUUID()

        provider.issueWithoutExpiration(memberId, MemberRole.USER)

        assertThat(parameters.captured.claims.subject).isEqualTo(memberId.toString())
        assertThat(parameters.captured.claims.getClaim<List<String>>("roles"))
            .containsExactly(MemberRole.USER.name)
        assertThat(parameters.captured.claims.expiresAt).isNull()
    }

    private fun token(): Jwt = Jwt.withTokenValue("token")
        .header("alg", "HS256")
        .subject("member")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .build()
}
