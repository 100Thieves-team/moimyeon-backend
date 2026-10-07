package io.plady.moimyeon.security.auth

import io.plady.moimyeon.core.enums.MemberRole
import io.plady.moimyeon.security.config.JwtConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import java.time.Instant
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

// 실제 키·인코더·디코더로 서명과 검증을 왕복한다. 용도 구분이 깨지면 복구 토큰으로 API 인증이 열린다.
class RestoreTokenProviderTest {
    private val jwtConfig = JwtConfig()
    private val key = SecretKeySpec("restore-token-test-secret-0123456789abcdef".toByteArray(), "HmacSHA256")
    private val encoder = jwtConfig.jwtEncoder(key)
    private val restoreTokenProvider = RestoreTokenProvider(encoder, key)
    private val memberId = UUID.randomUUID()

    @Test
    fun `복구 확인 토큰은 발급한 회원 id 와 발급 시각을 되돌려 준다`() {
        val before = Instant.now().minusSeconds(1)
        val token = restoreTokenProvider.issue(memberId)

        val claim = restoreTokenProvider.resolve(token)

        assertThat(claim?.memberId).isEqualTo(memberId)
        assertThat(claim?.issuedAt).isAfter(before)
    }

    @Test
    fun `만료되거나 다른 키로 서명되거나 형식이 깨진 복구 확인 토큰은 거절한다`() {
        val expired = encode(
            JwtClaimsSet.builder()
                .subject(memberId.toString())
                .issuedAt(Instant.now().minusSeconds(1200))
                .expiresAt(Instant.now().minusSeconds(600))
                .claim(RestoreTokenProvider.PURPOSE_CLAIM, RestoreTokenProvider.PURPOSE)
                .build(),
        )
        val otherKey = SecretKeySpec("another-secret-another-secret-0123456789".toByteArray(), "HmacSHA256")
        val foreign = RestoreTokenProvider(jwtConfig.jwtEncoder(otherKey), otherKey).issue(memberId)

        assertThat(restoreTokenProvider.resolve(expired)).isNull()
        assertThat(restoreTokenProvider.resolve(foreign)).isNull()
        assertThat(restoreTokenProvider.resolve("not-a-jwt")).isNull()
    }

    @Test
    fun `액세스 토큰은 복구 확인 토큰으로 쓸 수 없다`() {
        val accessToken = JwtTokenProvider(encoder).issue(memberId, MemberRole.USER)

        assertThat(restoreTokenProvider.resolve(accessToken)).isNull()
    }

    @Test
    fun `복구 확인 토큰을 액세스 토큰으로 쓰면 인증되지 않는다`() {
        val accessDecoder = jwtConfig.jwtDecoder(key)
        val restoreToken = restoreTokenProvider.issue(memberId)

        assertThatThrownBy { accessDecoder.decode(restoreToken) }.isInstanceOf(JwtException::class.java)
    }

    @Test
    fun `액세스 토큰과 개발용 액세스 토큰은 지금처럼 인증된다`() {
        val accessDecoder = jwtConfig.jwtDecoder(key)
        val tokens = JwtTokenProvider(encoder)

        assertThat(accessDecoder.decode(tokens.issue(memberId, MemberRole.USER)).subject).isEqualTo(memberId.toString())
        assertThat(accessDecoder.decode(tokens.issueWithoutExpiration(memberId, MemberRole.ADMIN)).subject)
            .isEqualTo(memberId.toString())
    }

    private fun encode(claims: JwtClaimsSet): String = encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .tokenValue
}
