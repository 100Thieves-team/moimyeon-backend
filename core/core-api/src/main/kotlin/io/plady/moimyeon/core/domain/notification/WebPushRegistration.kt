package io.plady.moimyeon.core.domain.notification

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness

@JvmInline
value class WebPushRegistration(
    val value: String,
) {
    init {
        requireBusiness(value.isNotBlank(), CoreErrorType.INVALID_WEB_PUSH_REGISTRATION)
    }
}
