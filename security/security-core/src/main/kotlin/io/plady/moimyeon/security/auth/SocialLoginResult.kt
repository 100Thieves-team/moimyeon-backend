package io.plady.moimyeon.security.auth

import java.util.UUID

sealed interface SocialLoginResult {
    data class Authenticated(val member: AuthenticatedMember) : SocialLoginResult

    data class Withdrawn(val memberId: UUID) : SocialLoginResult
}
