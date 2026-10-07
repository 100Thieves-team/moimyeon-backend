package io.plady.moimyeon.support.logging

// 메시지·인자·MDC·예외 메시지는 그대로 남긴다. 실수로 섞인 자격 증명 형태만 방어적으로 가린다.
object LogMasker {
    private val BEARER = Regex("(?i)\\bBearer\\s+[A-Za-z0-9\\-._~+/]+=*")
    private val JWT = Regex("\\beyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]*")

    fun mask(value: String): String = JWT.replace(BEARER.replace(value, "Bearer [MASKED]"), "[MASKED_JWT]")
}
