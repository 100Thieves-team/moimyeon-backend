package io.plady.moimyeon.core.domain.participation

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RoomParticipantService(
    private val participationValidator: ParticipationValidator,
    private val roomParticipantReader: RoomParticipantReader,
) {
    // 참여자 명부(「룸 참여」 §4.5). AI 이력서 요약과 제출 이력서 참조가 실리므로
    // 방장·참여자만 통과시킨다 - 신청자와 제3자는 §6 상 볼 수 없다.
    fun getParticipants(viewerMemberId: UUID, roomId: UUID): List<RoomParticipant> {
        participationValidator.validateParticipant(roomId, viewerMemberId)
        return roomParticipantReader.getAllByRoom(roomId, viewerMemberId)
    }
}
