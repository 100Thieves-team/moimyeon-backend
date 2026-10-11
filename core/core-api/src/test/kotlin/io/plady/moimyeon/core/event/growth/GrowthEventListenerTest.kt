package io.plady.moimyeon.core.event.growth

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.core.domain.analytics.AnalyticsIdGenerator
import io.plady.moimyeon.support.logging.GrowthEventEntry
import io.plady.moimyeon.support.logging.GrowthEventWriter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import java.util.UUID

class GrowthEventListenerTest {
    private val analyticsIdGenerator = mockk<AnalyticsIdGenerator>()
    private val growthEventWriter = mockk<GrowthEventWriter>(relaxed = true)
    private val growthEventListener = GrowthEventListener(analyticsIdGenerator, growthEventWriter)
    private val memberId = UUID.randomUUID()
    private val roomId = UUID.randomUUID()

    @Test
    fun `사건을 사건 코드와 주체의 analyticsId와 속성을 담은 그로스 엔트리로 기록한다`() {
        every { analyticsIdGenerator.generate(memberId) } returns ANALYTICS_ID
        val entry = slot<GrowthEventEntry>()

        growthEventListener.record(GrowthEvent(GrowthEventCode.ROOM_CREATED, memberId, mapOf("roomId" to roomId)))

        verify(exactly = 1) { growthEventWriter.write(capture(entry)) }
        assertThat(entry.captured.eventCode).isEqualTo("room.created")
        assertThat(entry.captured.analyticsId).isEqualTo(ANALYTICS_ID)
        assertThat(entry.captured.properties).containsEntry("roomId", roomId)
    }

    @Test
    fun `키가 없어 analyticsId를 만들지 못하면 analyticsId 없이 기록한다`() {
        every { analyticsIdGenerator.generate(memberId) } returns null
        val entry = slot<GrowthEventEntry>()

        growthEventListener.record(GrowthEvent(GrowthEventCode.MEMBER_SIGNED_UP, memberId))

        verify(exactly = 1) { growthEventWriter.write(capture(entry)) }
        assertThat(entry.captured.analyticsId).isNull()
    }

    @Test
    fun `기록에 실패해도 예외를 밖으로 던지지 않는다`() {
        every { analyticsIdGenerator.generate(memberId) } returns ANALYTICS_ID
        every { growthEventWriter.write(any()) } throws IllegalStateException("writer failed")

        assertThatCode { growthEventListener.record(GrowthEvent(GrowthEventCode.MEMBER_SIGNED_UP, memberId)) }
            .doesNotThrowAnyException()
    }

    companion object {
        private const val ANALYTICS_ID = "0123456789abcdef0123456789abcdef"
    }
}
