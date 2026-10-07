package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness

@JvmInline
value class RoomTitle(
    val value: String,
) {
    init {
        requireBusiness(value.isNotBlank(), CoreErrorType.INVALID_ROOM_TITLE)
        requireBusiness(value.length in LENGTH_RANGE, CoreErrorType.INVALID_ROOM_TITLE)
        requireBusiness(ALLOWED_PATTERN.matches(value), CoreErrorType.INVALID_ROOM_TITLE)
    }

    companion object {
        private val LENGTH_RANGE = 10..50

        // 모든 언어의 문자·숫자와 제목에 자주 쓰이는 특수문자 허용
        private val ALLOWED_PATTERN = Regex("""^[\p{L}\p{N} .,!?'\"“”()\[\]\-_/&+#:·]+$""")
    }
}
