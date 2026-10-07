package io.plady.moimyeon.core.event

import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.event.payload.RoomApplicationAcceptedEventPayload
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID

class OutboxEventPublisherTest {
    private val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val outboxEventPublisher = OutboxEventPublisher(applicationEventPublisher)

    @AfterEach
    fun tearDown() {
        TransactionSynchronizationManager.setActualTransactionActive(false)
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false)
    }

    @Test
    fun `트랜잭션 안에서 UUIDv7 식별자를 붙여 발행한다`() {
        TransactionSynchronizationManager.setActualTransactionActive(true)
        val published = slot<OutboxEvent>()

        outboxEventPublisher.publish(EventType.ROOM_APPLICATION_ACCEPTED, PAYLOAD)

        verify { applicationEventPublisher.publishEvent(capture(published)) }
        assertThat(published.captured.type).isEqualTo(EventType.ROOM_APPLICATION_ACCEPTED)
        assertThat(published.captured.payload).isEqualTo(PAYLOAD)
        assertThat(published.captured.eventId.version()).isEqualTo(7)
    }

    @Test
    fun `이벤트 종류와 payload 가 맞지 않으면 발행하지 않는다`() {
        TransactionSynchronizationManager.setActualTransactionActive(true)

        assertThatThrownBy { outboxEventPublisher.publish(EventType.ROOM_APPLICATION_REJECTED, PAYLOAD) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `읽기 전용 트랜잭션에서 발행하면 예외로 알린다`() {
        TransactionSynchronizationManager.setActualTransactionActive(true)
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true)

        assertThatThrownBy { outboxEventPublisher.publish(EventType.ROOM_APPLICATION_ACCEPTED, PAYLOAD) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `트랜잭션 밖에서 발행하면 예외로 알린다`() {
        assertThatThrownBy { outboxEventPublisher.publish(EventType.ROOM_APPLICATION_ACCEPTED, PAYLOAD) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    private companion object {
        val PAYLOAD = RoomApplicationAcceptedEventPayload(
            applicationId = 1L,
            roomId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            roomTitle = "토스 백엔드 모의면접",
            applicantMemberId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        )
    }
}
