package io.plady.moimyeon.core.event

import io.plady.moimyeon.core.enums.EventType
import org.springframework.stereotype.Component
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@Component
class OutboxEventSerializer(
    private val jsonMapper: JsonMapper,
) {
    fun serialize(event: OutboxEvent): String = jsonMapper.writeValueAsString(event)

    fun deserialize(json: String): OutboxEvent {
        val node = jsonMapper.readTree(json)
        val typeName = node["type"].asString()
        val type = EventType.entries.firstOrNull { it.name == typeName } ?: throw UnknownOutboxEventTypeException(typeName)
        return OutboxEvent(
            eventId = UUID.fromString(node["eventId"].asString()),
            type = type,
            // 필드가 빠진 이전 버전의 행도 읽도록 모르는 필드는 무시한다. 새 필드는 nullable 이나 기본값으로만 더한다.
            payload = jsonMapper.readerFor(type.payloadClass)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(node["payload"]),
        )
    }
}
