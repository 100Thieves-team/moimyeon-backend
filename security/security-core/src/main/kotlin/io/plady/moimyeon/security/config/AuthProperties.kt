package io.plady.moimyeon.security.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties(prefix = "security.auth")
data class AuthProperties(
    val cookie: Cookie,
    val cors: Cors,
    val oauth2: OAuth2,
) {
    data class Cookie(
        val accessTokenName: String,
        val refreshTokenName: String,
        val domain: String?, // 비어있으면 Domain 미지정 (localhost 개발용)
        val secure: Boolean,
        val sameSite: String,
        val accessMaxAgeSeconds: Long,
        val refreshMaxAgeSeconds: Long,
        val restoreTokenName: String = "RESTORE_TOKEN", // 탈퇴 계정 복구 확인 쿠키
    )

    data class Cors(
        val allowedOrigins: List<String>,
    )

    data class OAuth2(
        val successRedirectUri: URI,
        val failureRedirectUri: URI,
        val restoreRedirectUri: URI, // 탈퇴 회원 로그인 시 복구 확인 화면
    ) {
        init {
            require(successRedirectUri.isHttpUri()) { "OAuth2 success redirect URI must be an absolute HTTP(S) URI" }
            require(failureRedirectUri.isHttpUri()) { "OAuth2 failure redirect URI must be an absolute HTTP(S) URI" }
            require(restoreRedirectUri.isHttpUri()) { "OAuth2 restore redirect URI must be an absolute HTTP(S) URI" }
        }

        private fun URI.isHttpUri(): Boolean = isAbsolute && host != null && scheme.lowercase() in setOf("http", "https")
    }
}
