package io.plady.moimyeon.core.domain

@JvmInline
value class Email(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "이메일은 비어있을 수 없습니다." }
        require(EMAIL_REGEX.matches(value)) { "올바른 이메일 형식이 아닙니다: $value" }
    }

    companion object {
        private val EMAIL_REGEX =
            Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
    }
}
