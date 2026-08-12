package io.plady.moimyeon.core.domain.participation

import io.plady.moimyeon.core.domain.room.RoomLeaveManager
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RoomParticipantService(
    private val participationValidator: ParticipationValidator,
    private val roomParticipantReader: RoomParticipantReader,
    private val roomLeaveManager: RoomLeaveManager,
) {
    // 참여자 명부(「룸 참여」 §4.5). AI 이력서 요약과 제출 이력서 참조가 실리므로
    // 방장·참여자만 통과시킨다 - 신청자와 제3자는 §6 상 볼 수 없다.
    fun getParticipants(viewerMemberId: UUID, roomId: UUID): List<RoomParticipant> {
        participationValidator.validateParticipant(roomId, viewerMemberId)
        return roomParticipantReader.getAllByRoom(roomId, viewerMemberId)
    }

    // 나가기(「룸 참여」 §4.6). 참여 여부 판정이 나가기 규칙과 한 잠금 안에 있어야 해서
    // 여기서 validateParticipant 를 먼저 부르지 않는다 — Manager 가 룸을 잠근 뒤 함께 본다.
    fun leave(memberId: UUID, roomId: UUID) {
        roomLeaveManager.leave(roomId, memberId)
    }
}
