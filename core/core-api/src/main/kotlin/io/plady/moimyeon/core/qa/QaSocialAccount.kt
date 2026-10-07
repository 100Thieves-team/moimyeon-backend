package io.plady.moimyeon.core.qa

import io.plady.moimyeon.core.enums.SocialLoginProvider

data class QaSocialAccount(
    val provider: SocialLoginProvider,
    val providerId: String,
    val email: String,
)
