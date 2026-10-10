package io.plady.moimyeon.core.domain.analytics

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.util.HexFormat
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val log = KotlinLogging.logger {}

// 분석 도구로 보내는 가명 회원 식별자. 회원 ID를 키로 해시해 외부 값이 내부 DB 키와 바로 이어지지 않게 한다.
// 키를 바꾸면 모든 회원의 값이 바뀌어 분석 이력이 끊긴다.
@Component
class AnalyticsIdGenerator(
    properties: AnalyticsProperties,
) {
    private val key: SecretKeySpec? = properties.idHmacKey.takeIf { it.isNotBlank() }
        ?.let { SecretKeySpec(it.toByteArray(Charsets.UTF_8), ALGORITHM) }

    init {
        if (key == null) log.warn { "analytics.generator.disabled reason=missing-key" }
    }

    fun generate(memberId: UUID): String? {
        log.debug { "analytics.generator.generate memberId=$memberId" }
        val secret = key ?: return null
        val memberIdBytes = ByteBuffer.allocate(UUID_BYTES)
            .putLong(memberId.mostSignificantBits)
            .putLong(memberId.leastSignificantBits)
            .array()
        val digest = Mac.getInstance(ALGORITHM).apply { init(secret) }.doFinal(memberIdBytes)
        return HexFormat.of().formatHex(digest, 0, ID_BYTES)
    }

    companion object {
        private const val ALGORITHM = "HmacSHA256"
        private const val UUID_BYTES = 16
        private const val ID_BYTES = 16
    }
}
