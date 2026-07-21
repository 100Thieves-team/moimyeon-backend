package io.plady.moimyeon.core.domain

import io.plady.moimyeon.core.enums.SocialLoginProvider

data class SocialAccount(
    val provider: SocialLoginProvider,
    val providerId: String,
    val linkedEmail: Email?,
)
