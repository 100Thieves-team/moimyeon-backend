package io.plady.moimyeon.support.logging

import java.util.UUID

// DB 커밋이 끝난 업무 사건 한 건. 값은 식별자·숫자·enum만 받아 자유 입력이 분석 도구로 나가지 않게 한다.
// data class가 아닌 이유: 자동 toString이 일반 로그에 analyticsId를 흘리지 않게 한다.
class GrowthEventEntry(
    val eventCode: String,
    val eventId: UUID,
    val analyticsId: String?,
    properties: Map<String, Any> = emptyMap(),
) {
    // 검증 후 호출자가 원본 map을 바꿔도 출력이 바뀌지 않게 복사해 둔다.
    val properties: Map<String, Any> = properties.toMap()

    init {
        require(eventCode.length <= MAX_EVENT_CODE_LENGTH && EVENT_CODE.matches(eventCode)) { "Invalid growth event code" }
        require(analyticsId == null || ANALYTICS_ID.matches(analyticsId)) { "Invalid analytics ID for growth event" }
        require(this.properties.size <= MAX_PROPERTIES) { "Too many growth event properties" }
        this.properties.forEach { (key, value) ->
            require(key.length <= MAX_PROPERTY_KEY_LENGTH && PROPERTY_KEY.matches(key)) { "Invalid growth event property key" }
            require(isAllowedValue(value)) { "Invalid growth event property value" }
        }
    }

    internal fun fields(): Map<String, Any> = buildMap {
        put("eventCode", eventCode)
        put("message", eventCode)
        put("category", CATEGORY)
        put("eventId", eventId.toString())
        analyticsId?.let { put("analyticsId", it) }
        if (properties.isNotEmpty()) put("properties", properties.mapValues { (_, value) -> normalize(value) })
    }

    companion object {
        internal const val MARKER = "growth.event"
        internal const val PAYLOAD_KEY = "growth"
        private const val CATEGORY = "growth"
        private const val MAX_EVENT_CODE_LENGTH = 64
        private const val MAX_PROPERTIES = 16
        private const val MAX_PROPERTY_KEY_LENGTH = 64
        private val EVENT_CODE = Regex("[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*")
        private val ANALYTICS_ID = Regex("[0-9a-f]{32}")
        private val PROPERTY_KEY = Regex("[A-Za-z][A-Za-z0-9_]*")

        // 문자열은 형태 검사만으로 닉네임·전화번호를 걸러낼 수 없어 받지 않는다. 분류 값은 enum으로 넘긴다.
        private fun isAllowedValue(value: Any): Boolean = value is Int || value is Long || value is Boolean || value is UUID || value is Enum<*>

        private fun normalize(value: Any): Any = when (value) {
            is UUID -> value.toString()
            is Enum<*> -> value.name
            else -> value
        }
    }
}
