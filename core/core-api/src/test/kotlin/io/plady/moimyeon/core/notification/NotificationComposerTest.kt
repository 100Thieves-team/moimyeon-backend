package io.plady.moimyeon.core.notification

import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.NotificationPolicy
import io.plady.moimyeon.core.event.OutboxEvent
import io.plady.moimyeon.core.event.payload.EventPayload
import io.plady.moimyeon.core.event.payload.ReviewPublishedEventPayload
import io.plady.moimyeon.core.event.payload.RoomApplicationAcceptedEventPayload
import io.plady.moimyeon.core.event.payload.RoomApplicationRejectedEventPayload
import io.plady.moimyeon.core.event.payload.RoomApplicationSubmittedEventPayload
import io.plady.moimyeon.core.event.payload.RoomCanceledEventPayload
import io.plady.moimyeon.core.event.payload.RoomCommentPostedEventPayload
import io.plady.moimyeon.core.event.payload.RoomCompletedEventPayload
import io.plady.moimyeon.core.event.payload.RoomConfirmedEventPayload
import io.plady.moimyeon.core.event.payload.RoomHostDelegatedEventPayload
import io.plady.moimyeon.core.event.payload.RoomRecruitingReopenedEventPayload
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class NotificationComposerTest {
    private val composer = NotificationComposer()

    @Test
    fun `참여 신청은 방장에게 PUSH_ELSE_EMAIL 로 알린다`() {
        val messages = compose(
            EventType.ROOM_APPLICATION_SUBMITTED,
            RoomApplicationSubmittedEventPayload(1L, ROOM_ID, ROOM_TITLE, HOST, APPLICANT),
        )

        assertThat(messages.recipientsWith(NotificationPolicy.PUSH_ELSE_EMAIL)).containsExactly(HOST)
        assertThat(messages.single().body).contains(ROOM_TITLE)
        assertThat(messages.single().actionPath).isEqualTo("/rooms/$ROOM_ID")
    }

    @Test
    fun `수락은 신청자에게 PUSH_AND_EMAIL 로 알린다`() {
        val messages = compose(
            EventType.ROOM_APPLICATION_ACCEPTED,
            RoomApplicationAcceptedEventPayload(1L, ROOM_ID, ROOM_TITLE, APPLICANT),
        )

        assertThat(messages.recipientsWith(NotificationPolicy.PUSH_AND_EMAIL)).containsExactly(APPLICANT)
    }

    @Test
    fun `반려는 신청자에게 PUSH_ELSE_EMAIL 로 알린다`() {
        val messages = compose(
            EventType.ROOM_APPLICATION_REJECTED,
            RoomApplicationRejectedEventPayload(1L, ROOM_ID, ROOM_TITLE, APPLICANT),
        )

        assertThat(messages.recipientsWith(NotificationPolicy.PUSH_ELSE_EMAIL)).containsExactly(APPLICANT)
    }

    @Test
    fun `확정은 방장을 포함한 참여자에게 PUSH_AND_EMAIL, 신청이 닫힌 대기 신청자에게 PUSH_ELSE_EMAIL 로 알린다`() {
        val messages = compose(
            EventType.ROOM_CONFIRMED,
            RoomConfirmedEventPayload(ROOM_ID, ROOM_TITLE, HOST, listOf(HOST, PARTICIPANT_A, PARTICIPANT_B), listOf(APPLICANT)),
        )

        assertThat(messages.recipientsWith(NotificationPolicy.PUSH_AND_EMAIL)).containsExactly(HOST, PARTICIPANT_A, PARTICIPANT_B)
        assertThat(messages.recipientsWith(NotificationPolicy.PUSH_ELSE_EMAIL)).containsExactly(APPLICANT)
    }

    @Test
    fun `완료는 확정 참여자 전원에게 한 번씩 알리고 출석자에게만 후기를 요청한다`() {
        val messages = compose(
            EventType.ROOM_COMPLETED,
            RoomCompletedEventPayload(ROOM_ID, ROOM_TITLE, HOST, listOf(HOST, PARTICIPANT_A, PARTICIPANT_B), listOf(HOST, PARTICIPANT_A)),
        )

        assertThat(messages.recipientsWith(NotificationPolicy.PUSH_ELSE_EMAIL)).containsExactly(HOST, PARTICIPANT_A, PARTICIPANT_B)
        assertThat(messages.bodyOf(HOST)).contains("후기")
        assertThat(messages.bodyOf(PARTICIPANT_A)).contains("후기")
        assertThat(messages.bodyOf(PARTICIPANT_B)).contains("불참").doesNotContain("후기")
    }

    @Test
    fun `출석자가 한 명이면 후기를 쓸 상대가 없어 후기를 요청하지 않는다`() {
        val messages = compose(
            EventType.ROOM_COMPLETED,
            RoomCompletedEventPayload(ROOM_ID, ROOM_TITLE, HOST, listOf(HOST, PARTICIPANT_A), listOf(HOST)),
        )

        assertThat(messages.bodyOf(HOST)).doesNotContain("후기").doesNotContain("불참")
        assertThat(messages.bodyOf(PARTICIPANT_A)).contains("불참")
    }

    @Test
    fun `취소는 취소한 방장과 참여자, 신청이 닫힌 대기 신청자에게 PUSH_AND_EMAIL 로 알린다`() {
        val messages = compose(
            EventType.ROOM_CANCELED,
            RoomCanceledEventPayload(ROOM_ID, ROOM_TITLE, HOST, listOf(HOST, PARTICIPANT_A), listOf(APPLICANT)),
        )

        assertThat(messages.recipientsWith(NotificationPolicy.PUSH_AND_EMAIL)).containsExactly(HOST, PARTICIPANT_A, APPLICANT)
    }

    @Test
    fun `방장이 나가 취소되면 참여자가 없어도 나간 방장에게 알린다`() {
        val messages = compose(EventType.ROOM_CANCELED, RoomCanceledEventPayload(ROOM_ID, ROOM_TITLE, HOST, emptyList(), emptyList()))

        assertThat(messages.map { it.recipientMemberId }).containsExactly(HOST)
    }

    @Test
    fun `방장 위임은 남은 참여자 전원에게 PUSH_ELSE_EMAIL 로 알리고 새 방장에게는 다른 문구를 보낸다`() {
        val messages = compose(
            EventType.ROOM_HOST_DELEGATED,
            RoomHostDelegatedEventPayload(ROOM_ID, ROOM_TITLE, HOST, PARTICIPANT_A, listOf(PARTICIPANT_A, PARTICIPANT_B)),
        )

        assertThat(messages.recipientsWith(NotificationPolicy.PUSH_ELSE_EMAIL)).containsExactly(PARTICIPANT_A, PARTICIPANT_B)
        assertThat(messages.single { it.recipientMemberId == PARTICIPANT_A }.title).isEqualTo("방장이 되었어요")
        assertThat(messages.single { it.recipientMemberId == PARTICIPANT_B }.title).isEqualTo("방장이 바뀌었어요")
    }

    @Test
    fun `모집 재개는 방장과 남은 참여자에게 PUSH_ELSE_EMAIL 로 알리고 방장에게는 다른 문구를 보낸다`() {
        val messages = compose(
            EventType.ROOM_RECRUITING_REOPENED,
            RoomRecruitingReopenedEventPayload(ROOM_ID, ROOM_TITLE, HOST, listOf(HOST, PARTICIPANT_A)),
        )

        assertThat(messages.recipientsWith(NotificationPolicy.PUSH_ELSE_EMAIL)).containsExactly(HOST, PARTICIPANT_A)
        assertThat(messages.single { it.recipientMemberId == HOST }.title).isEqualTo("참여자가 나가 모집이 다시 열렸어요")
        assertThat(messages.single { it.recipientMemberId == PARTICIPANT_A }.title).isEqualTo("모집이 다시 열렸어요")
        assertThat(messages.map { it.body }).allMatch { it.contains(ROOM_TITLE) }
    }

    @Test
    fun `후기 공개는 대상자에게 PUSH_ONLY 로 알리고 작성자를 드러내지 않는다`() {
        val messages = compose(
            EventType.REVIEW_PUBLISHED,
            ReviewPublishedEventPayload(1L, ROOM_ID, ROOM_TITLE, PARTICIPANT_A, PARTICIPANT_B),
        )

        assertThat(messages.recipientsWith(NotificationPolicy.PUSH_ONLY)).containsExactly(PARTICIPANT_B)
        assertThat(messages.single().toString()).doesNotContain(PARTICIPANT_A.toString())
    }

    @Test
    fun `자기 자신에게 쓴 후기는 알리지 않는다`() {
        val messages = compose(
            EventType.REVIEW_PUBLISHED,
            ReviewPublishedEventPayload(1L, ROOM_ID, ROOM_TITLE, PARTICIPANT_A, PARTICIPANT_A),
        )

        assertThat(messages).isEmpty()
    }

    @Test
    fun `댓글은 작성자를 뺀 참여자에게 PUSH_ONLY 로 알린다`() {
        val messages = compose(
            EventType.ROOM_COMMENT_POSTED,
            RoomCommentPostedEventPayload(1L, ROOM_ID, ROOM_TITLE, PARTICIPANT_A, listOf(HOST, PARTICIPANT_A, PARTICIPANT_B)),
        )

        assertThat(messages.recipientsWith(NotificationPolicy.PUSH_ONLY)).containsExactly(HOST, PARTICIPANT_B)
    }

    private fun compose(type: EventType, payload: EventPayload) = composer.compose(OutboxEvent(UUID.randomUUID(), type, payload))

    private fun List<ComposedNotification>.recipientsWith(policy: NotificationPolicy) = filter { it.policy == policy }.map { it.recipientMemberId }

    private fun List<ComposedNotification>.bodyOf(recipient: UUID) = single { it.recipientMemberId == recipient }.body

    private companion object {
        const val ROOM_TITLE = "토스 백엔드 모의면접"
        val ROOM_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val HOST: UUID = UUID.fromString("00000000-0000-0000-0000-00000000000a")
        val APPLICANT: UUID = UUID.fromString("00000000-0000-0000-0000-00000000000b")
        val PARTICIPANT_A: UUID = UUID.fromString("00000000-0000-0000-0000-00000000000c")
        val PARTICIPANT_B: UUID = UUID.fromString("00000000-0000-0000-0000-00000000000d")
    }
}
