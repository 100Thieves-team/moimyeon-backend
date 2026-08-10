package io.plady.moimyeon.security.auth

import io.plady.moimyeon.core.enums.MemberRole
import java.util.UUID

data class AuthenticatedMember(
    val id: UUID,
    val role: MemberRole,
)
