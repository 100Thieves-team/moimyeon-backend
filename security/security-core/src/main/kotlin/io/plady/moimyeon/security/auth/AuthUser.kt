package io.plady.moimyeon.security.auth

import java.util.UUID

// 컨트롤러가 인증 결과로 받는 유일한 타입 — spring-security 의존이 없는 순수 DTO 로 유지할 것
data class AuthUser(
    val id: UUID,
)
