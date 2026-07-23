package io.plady.moimyeon.core.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object RefreshTokenGenerator {
    private val random = SecureRandom()

    fun generate(): String = // 노출용 원문(쿠키로만 나감), 256bit
        ByteArray(32).also { random.nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    fun hash(raw: String): String = // 저장(DB엔 이것만)
        MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
}
