package io.plady.moimyeon.core.domain.member

import java.util.UUID

sealed interface SocialAuthentication {
    val memberId: UUID

    data class LoggedIn(override val memberId: UUID) : SocialAuthentication

    data class Withdrawn(override val memberId: UUID) : SocialAuthentication
}
