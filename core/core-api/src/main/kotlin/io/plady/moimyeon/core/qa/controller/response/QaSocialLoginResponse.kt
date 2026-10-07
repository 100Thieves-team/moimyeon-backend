package io.plady.moimyeon.core.qa.controller.response

import io.plady.moimyeon.security.auth.SocialLanding
import java.util.UUID

data class QaSocialLoginResponse(
    val memberId: UUID,
    val outcome: SocialLanding.Outcome,
    val redirectUri: String,
    val cookies: List<String>,
) {
    companion object {
        fun from(landing: SocialLanding): QaSocialLoginResponse = QaSocialLoginResponse(
            memberId = landing.memberId,
            outcome = landing.outcome,
            redirectUri = landing.redirectUri.toString(),
            cookies = landing.cookies.map { it.name },
        )
    }
}
