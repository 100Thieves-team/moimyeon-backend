package io.plady.moimyeon.core.notification

import io.plady.moimyeon.core.enums.NotificationPolicy
import io.plady.moimyeon.core.event.OutboxEvent
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
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class NotificationComposer {
    fun compose(event: OutboxEvent): List<ComposedNotification> = when (val payload = event.payload) {
        is RoomApplicationSubmittedEventPayload -> listOf(
            roomMessage(
                payload.hostMemberId,
                NotificationPolicy.PUSH_ELSE_EMAIL,
                payload.roomId,
                "새 참가 신청이 왔어요",
                "'${payload.roomTitle}'에 참가 신청이 들어왔어요. 신청 내용을 확인해 주세요.",
            ),
        )

        is RoomApplicationAcceptedEventPayload -> listOf(
            roomMessage(
                payload.applicantMemberId,
                NotificationPolicy.PUSH_AND_EMAIL,
                payload.roomId,
                "참가 신청이 수락되었어요",
                "'${payload.roomTitle}' 모임에 참여할 수 있게 되었어요.",
            ),
        )

        // 반려 사유는 알림을 눌러 들어간 화면에서 조회한다(MOI-542).
        is RoomApplicationRejectedEventPayload -> listOf(
            roomMessage(
                payload.applicantMemberId,
                NotificationPolicy.PUSH_ELSE_EMAIL,
                payload.roomId,
                "참가 신청 결과를 알려드려요",
                "'${payload.roomTitle}' 참가 신청이 수락되지 않았어요.",
            ),
        )

        is RoomConfirmedEventPayload ->
            payload.participantMemberIds.map {
                roomMessage(
                    it,
                    NotificationPolicy.PUSH_AND_EMAIL,
                    payload.roomId,
                    "모임이 확정되었어요",
                    "'${payload.roomTitle}' 모임이 확정되었어요. 일정을 다시 확인해 주세요.",
                )
            } + payload.closedApplicantMemberIds.map {
                roomMessage(
                    it,
                    NotificationPolicy.PUSH_ELSE_EMAIL,
                    payload.roomId,
                    "참가 신청이 마감되었어요",
                    "'${payload.roomTitle}' 모임이 확정되어 참가 신청이 마감되었어요.",
                )
            }

        is RoomCompletedEventPayload -> {
            val attended = payload.attendedMemberIds.toSet()
            // 후기를 쓸 상대가 있을 때만 후기를 요청한다.
            val reviewRequested = attended.size >= MIN_ATTENDEES_FOR_REVIEW_REQUEST
            payload.confirmedParticipantMemberIds.map {
                val body = when {
                    it !in attended -> "'${payload.roomTitle}' 모임이 완료되었어요. 이번 모임은 불참으로 기록되었어요."
                    reviewRequested -> "'${payload.roomTitle}' 모임이 완료되었어요. 함께한 참여자의 후기를 남겨 주세요."
                    else -> "'${payload.roomTitle}' 모임이 완료되었어요."
                }
                roomMessage(it, NotificationPolicy.PUSH_ELSE_EMAIL, payload.roomId, "모임이 완료되었어요", body)
            }
        }

        // 넘겨받을 사람이 없어 취소된 것이므로 나간 방장도 받는다.
        is RoomCanceledEventPayload ->
            (payload.participantMemberIds + payload.canceledByMemberId).distinct().map {
                roomMessage(
                    it,
                    NotificationPolicy.PUSH_AND_EMAIL,
                    payload.roomId,
                    "모임이 취소되었어요",
                    "'${payload.roomTitle}' 모임이 취소되었어요.",
                )
            } + payload.closedApplicantMemberIds.map {
                roomMessage(
                    it,
                    NotificationPolicy.PUSH_AND_EMAIL,
                    payload.roomId,
                    "모임이 취소되었어요",
                    "'${payload.roomTitle}' 모임이 취소되어 참가 신청이 종료되었어요.",
                )
            }

        is RoomHostDelegatedEventPayload -> payload.participantMemberIds.map {
            if (it == payload.newHostMemberId) {
                roomMessage(
                    it,
                    NotificationPolicy.PUSH_ELSE_EMAIL,
                    payload.roomId,
                    "방장이 되었어요",
                    "'${payload.roomTitle}' 모임의 방장을 이어받았어요.",
                )
            } else {
                roomMessage(
                    it,
                    NotificationPolicy.PUSH_ELSE_EMAIL,
                    payload.roomId,
                    "방장이 바뀌었어요",
                    "'${payload.roomTitle}' 모임의 방장이 바뀌었어요.",
                )
            }
        }

        is RoomRecruitingReopenedEventPayload -> payload.participantMemberIds.map {
            if (it == payload.hostMemberId) {
                roomMessage(
                    it,
                    NotificationPolicy.PUSH_ELSE_EMAIL,
                    payload.roomId,
                    "참여자가 나가 모집이 다시 열렸어요",
                    "'${payload.roomTitle}' 모임 인원이 최소 진행 인원보다 적어져 모집 중으로 돌아갔어요. 다시 모집해 확정해 주세요.",
                )
            } else {
                roomMessage(
                    it,
                    NotificationPolicy.PUSH_ELSE_EMAIL,
                    payload.roomId,
                    "모집이 다시 열렸어요",
                    "'${payload.roomTitle}' 모임 인원이 줄어 모집 중으로 돌아갔어요. 다시 확정되면 알려 드릴게요.",
                )
            }
        }

        is ReviewPublishedEventPayload ->
            if (payload.authorMemberId == payload.targetMemberId) {
                emptyList()
            } else {
                listOf(
                    roomMessage(
                        payload.targetMemberId,
                        NotificationPolicy.PUSH_ONLY,
                        payload.roomId,
                        "새 후기가 도착했어요",
                        "'${payload.roomTitle}' 모임에서 받은 후기가 있어요.",
                    ),
                )
            }

        is RoomCommentPostedEventPayload -> (payload.participantMemberIds - payload.authorMemberId).map {
            roomMessage(
                it,
                NotificationPolicy.PUSH_ONLY,
                payload.roomId,
                "새 댓글이 달렸어요",
                "'${payload.roomTitle}'에 새 댓글이 달렸어요.",
            )
        }
    }

    private fun roomMessage(
        recipientMemberId: UUID,
        policy: NotificationPolicy,
        roomId: UUID,
        title: String,
        body: String,
    ) = ComposedNotification(
        recipientMemberId = recipientMemberId,
        policy = policy,
        title = title,
        body = body,
        actionPath = "/rooms/$roomId",
    )

    private companion object {
        const val MIN_ATTENDEES_FOR_REVIEW_REQUEST = 2
    }
}
