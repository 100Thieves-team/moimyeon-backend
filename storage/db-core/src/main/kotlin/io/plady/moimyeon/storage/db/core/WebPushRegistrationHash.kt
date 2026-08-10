package io.plady.moimyeon.storage.db.core

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat

object WebPushRegistrationHash {
    fun of(registration: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(registration.toByteArray(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }
}
