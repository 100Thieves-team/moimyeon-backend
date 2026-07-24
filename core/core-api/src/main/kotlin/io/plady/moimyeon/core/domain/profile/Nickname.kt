package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness

@JvmInline
value class Nickname(
    val value: String,
) {
    init {
        requireBusiness(value.length in LENGTH_RANGE, CoreErrorType.INVALID_NICKNAME)
        requireBusiness(PATTERN.matches(value), CoreErrorType.INVALID_NICKNAME)
        requireBusiness(FORBIDDEN_WORDS.none { value.contains(it) }, CoreErrorType.INVALID_NICKNAME)
    }

    companion object {
        // TODO(MOI-316): 길이·문자·금칙어 세부 정책은 기획 확정 시 갱신한다.
        private val LENGTH_RANGE = 2..20
        private val PATTERN = Regex("^[가-힣a-zA-Z0-9 ]+$")
        private val FORBIDDEN_WORDS = setOf("운영자", "관리자", "모이면")
    }
}
