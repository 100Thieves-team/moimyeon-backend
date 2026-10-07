package io.plady.moimyeon.security.auth

import java.time.LocalDateTime

data class IssuedSession(
    val credential: String, // 클라이언트가 쥐는 리프레시 토큰 원문
    val expiresAt: LocalDateTime,
)
