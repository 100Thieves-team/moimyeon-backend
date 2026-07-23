package io.plady.moimyeon.security.config

import org.springframework.boot.context.properties.ConfigurationProperties

// 쿠키 속성/CORS 오리진을 프로파일(local ↔ 운영)별로 분기하기 위한 바인딩 클래스
@ConfigurationProperties(prefix = "security.auth")
data class AuthProperties(
    val cookie: Cookie,
    val cors: Cors,
) {
    data class Cookie(
        val domain: String?, // 비어있으면 Domain 미지정 (localhost 개발용)
        val secure: Boolean,
        val sameSite: String,
        val accessMaxAgeSeconds: Long, // AT 쿠키
        val refreshMaxAgeSeconds: Long, // RT 쿠키
    )

    data class Cors(
        val allowedOrigins: List<String>,
    )
}
