package io.plady.moimyeon.core.domain.member

import org.springframework.dao.DataIntegrityViolationException

internal const val MEMBER_NICKNAME_UNIQUE_CONSTRAINT = "uk_member_nickname"
internal const val MEMBER_SOCIAL_ACCOUNT_UNIQUE_CONSTRAINT = "uk_social_account_provider_provider_id"

internal fun DataIntegrityViolationException.matchesConstraint(name: String): Boolean {
    return (rootCause?.message ?: message).orEmpty().contains(name, ignoreCase = true)
}
