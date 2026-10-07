package io.plady.moimyeon.core.qa.controller.response

import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.qa.QaData
import io.plady.moimyeon.core.qa.QaMember
import io.plady.moimyeon.core.qa.QaRoom
import java.time.LocalDateTime
import java.util.UUID

data class QaDataResponse(
    val rooms: List<QaRoomResponse>,
    val members: List<QaMemberSummaryResponse>,
) {
    companion object {
        fun from(data: QaData): QaDataResponse = QaDataResponse(
            rooms = data.rooms.map(QaRoomResponse::from),
            members = data.members.map(QaMemberSummaryResponse::from),
        )
    }
}

data class QaMemberSummaryResponse(
    val memberId: UUID,
    val nickname: String,
    val email: String,
) {
    companion object {
        fun from(member: QaMember): QaMemberSummaryResponse = QaMemberSummaryResponse(
            memberId = member.id,
            nickname = member.nickname,
            email = member.email,
        )
    }
}

data class QaRoomResponse(
    val roomId: UUID,
    val title: String,
    val status: RoomStatus,
    val hostMemberId: UUID?,
    val createdAt: LocalDateTime,
    val counts: QaRoomCountsResponse,
) {
    companion object {
        fun from(room: QaRoom): QaRoomResponse = QaRoomResponse(
            roomId = room.id,
            title = room.title,
            status = room.status,
            hostMemberId = room.hostMemberId,
            createdAt = room.createdAt,
            counts = QaRoomCountsResponse(
                applications = room.applicationCount,
                participants = room.participantCount,
            ),
        )
    }
}

data class QaRoomCountsResponse(
    val applications: Long,
    val participants: Long,
)
