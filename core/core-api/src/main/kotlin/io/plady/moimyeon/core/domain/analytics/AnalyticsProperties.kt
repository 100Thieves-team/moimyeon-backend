package io.plady.moimyeon.core.domain.analytics

import org.springframework.boot.context.properties.ConfigurationProperties

// 키가 비어 있으면 analyticsId를 만들지 않는다. 키 주입 전 배포에서도 기동은 되게 한다.
@ConfigurationProperties("moimyeon.analytics")
data class AnalyticsProperties(
    val idHmacKey: String = "",
) {
    init {
        require(idHmacKey.isBlank() || idHmacKey.toByteArray(Charsets.UTF_8).size >= MIN_KEY_BYTES) {
            "moimyeon.analytics.id-hmac-key must be at least $MIN_KEY_BYTES bytes"
        }
    }

    // data class 기본 toString이 키 원문을 드러내지 않게 한다.
    override fun toString(): String = "AnalyticsProperties(idHmacKey=${if (idHmacKey.isBlank()) "" else "****"})"

    companion object {
        private const val MIN_KEY_BYTES = 32
    }
}
