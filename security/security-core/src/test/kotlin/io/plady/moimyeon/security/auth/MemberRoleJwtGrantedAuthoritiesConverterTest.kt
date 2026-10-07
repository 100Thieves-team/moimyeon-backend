package io.plady.moimyeon.security.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class MemberRoleJwtGrantedAuthoritiesConverterTest {
    @Test
    fun `roles claim을 Spring Security 역할 권한으로 변환한다`() {
        val jwt = token(listOf("ADMIN"))

        val authorities = MemberRoleJwtGrantedAuthoritiesConverter().convert(jwt)

        assertThat(authorities.map { it.authority }).containsExactly("ROLE_ADMIN")
    }

    @Test
    fun `roles claim이 없으면 역할 권한도 없다`() {
        assertThat(MemberRoleJwtGrantedAuthoritiesConverter().convert(token()).toList()).isEmpty()
    }

    private fun token(roles: List<String>? = null): Jwt {
        val builder = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("member")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
        roles?.let { builder.claim("roles", it) }
        return builder.build()
    }
}
