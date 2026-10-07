package io.plady.moimyeon.core.domain.session

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class SessionCredential private constructor(
    val value: String,
) {
    fun hash(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is SessionCredential && value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "SessionCredential(***)"

    companion object {
        private val random = SecureRandom()

        fun issue(): SessionCredential {
            val value = ByteArray(32)
                .also { random.nextBytes(it) }
                .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
            return SessionCredential(value)
        }

        fun from(value: String): SessionCredential = SessionCredential(value)
    }
}
