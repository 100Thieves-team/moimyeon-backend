package io.plady.moimyeon.support.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.oshai.kotlinlogging.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.json.JsonParserFactory
import org.springframework.mock.env.MockEnvironment
import java.util.UUID

@ResourceLock("logback")
class GrowthLoggingTest {
    @Test
    fun `그로스 사건을 기록하면 사건명과 growth 분류와 식별자와 속성을 JSON에 남긴다`() {
        capture { writer, events ->
            writer.write(GrowthEventEntry("room.created", EVENT_ID, ANALYTICS_ID, mapOf("roomId" to ROOM_ID, "minParticipants" to 3)))

            assertThat(events.single().level).isEqualTo(Level.INFO)
            val fields = json(events.single())
            assertThat(fields).containsEntry("eventCode", "room.created")
                .containsEntry("message", "room.created")
                .containsEntry("category", "growth")
                .containsEntry("eventId", EVENT_ID.toString())
                .containsEntry("analyticsId", ANALYTICS_ID)
            @Suppress("UNCHECKED_CAST")
            val properties = fields["properties"] as Map<String, Any>
            assertThat(properties).containsEntry("roomId", ROOM_ID.toString())
            assertThat((properties["minParticipants"] as Number).toInt()).isEqualTo(3)
        }
    }

    @Test
    fun `analyticsId가 없는 사건은 analyticsId 필드 없이 기록한다`() {
        capture { writer, events ->
            writer.write(GrowthEventEntry("member.withdrew", EVENT_ID, null))

            val fields = json(events.single())
            assertThat(fields).containsEntry("eventCode", "member.withdrew").containsEntry("category", "growth")
                .doesNotContainKey("analyticsId")
        }
    }

    @Test
    fun `사건명은 대상과 과거형 동사의 소문자 스네이크 형식만 허용한다`() {
        for (code in listOf("room.created", "room_application.accepted")) {
            assertThatCode { GrowthEventEntry(code, EVENT_ID, null) }.doesNotThrowAnyException()
        }
        for (code in listOf("Room.Created", "room", "room.created.extra", "room created", "room." + "a".repeat(60))) {
            assertThatThrownBy { GrowthEventEntry(code, EVENT_ID, null) }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `analyticsId는 32자리 소문자 16진수만 허용한다`() {
        for (id in listOf("a".repeat(31), "a".repeat(33), "A".repeat(32), "g".repeat(32), UUID.randomUUID().toString())) {
            assertThatThrownBy { GrowthEventEntry("room.created", EVENT_ID, id) }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `속성 값은 숫자 Boolean UUID enum만 허용하고 문자열은 거부한다`() {
        val allowed = mapOf("count" to 3, "total" to 3L, "first" to true, "roomId" to ROOM_ID, "reason" to Reason.SCHEDULE_TIMEOUT)
        assertThatCode { GrowthEventEntry("room.completed", EVENT_ID, null, allowed) }.doesNotThrowAnyException()

        for (value in listOf("HOST", "hong_gildong", "010-1234-5678", "private@example.invalid", 1.5, listOf(1), mapOf("a" to 1), Any())) {
            assertThatThrownBy { GrowthEventEntry("room.completed", EVENT_ID, null, mapOf("value" to value)) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `생성 후 원본 속성 map을 바꿔도 기록되는 속성은 바뀌지 않는다`() {
        capture { writer, events ->
            val source = mutableMapOf<String, Any>("minParticipants" to 3)
            val entry = GrowthEventEntry("room.created", EVENT_ID, null, source)
            source["nickname"] = "hong gildong"

            writer.write(entry)

            @Suppress("UNCHECKED_CAST")
            val properties = json(events.single())["properties"] as Map<String, Any>
            assertThat(properties).containsOnlyKeys("minParticipants")
        }
    }

    @Test
    fun `속성 키는 영문자로 시작하는 영숫자와 밑줄만 허용하고 17개 이상이면 거부한다`() {
        for (key in listOf("1room", "room id", "room.id", "room-id", "a".repeat(65))) {
            assertThatThrownBy { GrowthEventEntry("room.created", EVENT_ID, null, mapOf(key to 1)) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
        assertThatCode { GrowthEventEntry("room.created", EVENT_ID, null, (1..16).associate { "p$it" to it }) }.doesNotThrowAnyException()
        assertThatThrownBy { GrowthEventEntry("room.created", EVENT_ID, null, (1..17).associate { "p$it" to it }) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `MDC나 key-value로 그로스 예약 필드를 주입해도 무시한다`() {
        capture { _, events ->
            val forged = listOf("category", "eventId", "analyticsId", "properties")
            forged.forEach { MDC.put(it, "forged") }
            try {
                KotlinLogging.logger(LOGGER_NAME).atInfo {
                    message = "room.created"
                    payload = forged.associateWith { "forged" }
                }
            } finally {
                forged.forEach(MDC::remove)
            }

            val fields = json(events.single())
            assertThat(fields).containsEntry("eventCode", "application.log").doesNotContainKeys(*forged.toTypedArray())
        }
    }

    @Test
    fun `그로스 사건 기록 중 MDC의 예약 필드는 사건 값을 덮어쓰지 못한다`() {
        capture { writer, events ->
            MDC.put("analyticsId", "forged")
            MDC.put("properties", "forged")
            try {
                writer.write(GrowthEventEntry("room.created", EVENT_ID, ANALYTICS_ID, mapOf("minParticipants" to 3)))
            } finally {
                MDC.remove("analyticsId")
                MDC.remove("properties")
            }

            val fields = json(events.single())
            assertThat(fields).containsEntry("analyticsId", ANALYTICS_ID)
            assertThat(fields["properties"]).isInstanceOf(Map::class.java)
        }
    }

    @Test
    fun `그로스 표식 없이 그로스 엔트리를 payload에 넣은 일반 로그는 그로스로 승격하지 않는다`() {
        capture { _, events ->
            KotlinLogging.logger(LOGGER_NAME).atInfo {
                message = "room.created"
                payload = mapOf(GrowthEventEntry.PAYLOAD_KEY to GrowthEventEntry("room.created", EVENT_ID, ANALYTICS_ID))
            }

            val fields = json(events.single())
            assertThat(fields).containsEntry("eventCode", "application.log")
                .doesNotContainKeys("category", "eventId", "analyticsId", "properties", GrowthEventEntry.PAYLOAD_KEY)
            assertThat(fields.values.map { it.toString() }).noneMatch { it.contains(ANALYTICS_ID) }
        }
    }

    @Test
    fun `INFO가 아닌 수준으로 남긴 그로스 표식 로그는 그로스로 승격하지 않고 엔트리도 남기지 않는다`() {
        capture { _, events ->
            KotlinLogging.logger(LOGGER_NAME).atWarn {
                message = GrowthEventEntry.MARKER
                payload = mapOf(GrowthEventEntry.PAYLOAD_KEY to GrowthEventEntry("room.created", EVENT_ID, ANALYTICS_ID))
            }

            val fields = json(events.single())
            assertThat(fields).containsEntry("eventCode", "application.log")
                .doesNotContainKeys("category", "eventId", "analyticsId", "properties", GrowthEventEntry.PAYLOAD_KEY)
        }
    }

    @Test
    fun `로컬 텍스트 형식에서도 그로스 사건은 한 줄로 출력한다`() {
        capture { writer, events ->
            writer.write(GrowthEventEntry("room.created", EVENT_ID, ANALYTICS_ID, mapOf("roomId" to ROOM_ID)))

            val text = SafeLogFormatter(MockEnvironment().withProperty("moimyeon.logging.environment", "local")).formatText(events.single())
            assertThat(text.trimEnd('\n')).doesNotContain("\n").contains("room.created").contains("category=growth")
        }
    }

    private enum class Reason { SCHEDULE_TIMEOUT }

    private fun capture(block: (GrowthEventWriter, List<ILoggingEvent>) -> Unit) {
        val logger = (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger(LOGGER_NAME)
        val oldLevel = logger.level
        val oldAdditive = logger.isAdditive
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.level = Level.INFO
        logger.isAdditive = false
        logger.addAppender(appender)
        try {
            block(GrowthEventWriter(KotlinLogging.logger(LOGGER_NAME)), appender.list)
        } finally {
            logger.detachAppender(appender)
            logger.level = oldLevel
            logger.isAdditive = oldAdditive
            appender.stop()
        }
    }

    private fun json(event: ILoggingEvent): Map<String, Any> = JsonParserFactory.getJsonParser().parseMap(
        SafeLogFormatter(MockEnvironment().withProperty("moimyeon.logging.environment", "test")).format(event),
    )

    companion object {
        private const val LOGGER_NAME = "io.plady.moimyeon.growth.logging.test"
        private const val ANALYTICS_ID = "0123456789abcdef0123456789abcdef"
        private val EVENT_ID = UUID.fromString("019daf00-0000-7000-8000-000000000001")
        private val ROOM_ID = UUID.fromString("019daf00-0000-7000-8000-000000000002")
    }
}
