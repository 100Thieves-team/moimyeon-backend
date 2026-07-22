package io.plady.moimyeon.security.config

import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

// 자체 JWT 발급/검증용 HMAC 대칭키. secret 은 32바이트 이상 랜덤(환경변수 주입).
@Configuration
class JwtConfig {
    @Bean
    fun jwtSecretKey(
        @Value("\${security.jwt.secret}") secret: String,
    ): SecretKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")

    @Bean
    fun jwtEncoder(key: SecretKey): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(key))

    @Bean
    fun jwtDecoder(key: SecretKey): JwtDecoder = NimbusJwtDecoder.withSecretKey(key).build()
}
