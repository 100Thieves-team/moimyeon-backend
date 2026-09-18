package io.plady.moimyeon.core.domain.participation

import io.plady.moimyeon.core.domain.room.RoomLeaveManager
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RoomParticipantService(
    private val participationValidator: ParticipationValidator,
    private val roomParticipantReader: RoomParticipantReader,
    private val participationFinder: ParticipationFinder,
    private val roomLeaveManager: RoomLeaveManager,
) {
    fun getParticipatingRoomIds(memberId: UUID): List<UUID> = participationFinder.getParticipatingRoomIds(memberId)

    // 참여 슬롯 여유분(MOI-447). 신청·수락·생성 게이트와 같은 판정(ParticipationSlot)을 조회로 노출한다.
    fun getParticipationSlots(memberId: UUID): ParticipationSlots = participationFinder.getSlots(memberId)

    // 참여자 명부(「룸 참여」 §4.5). AI 이력서 요약과 제출 이력서 참조가 실리므로
    // 방장·참여자만 통과시킨다 - 신청자와 제3자는 §6 상 볼 수 없다.
    fun getParticipants(viewerMemberId: UUID, roomId: UUID): List<RoomParticipant> {
        participationValidator.validateParticipant(roomId, viewerMemberId)
        return roomParticipantReader.getAllByRoom(roomId, viewerMemberId)
    }

    // 방명록 글의 방장·(퇴장) 뱃지 판정용(MOI-461). 참여자 게이트는 방명록 Service 가 이미
    // 통과한 뒤의 응답 조립에서만 쓴다 - 명부(getParticipants)와 달리 이력서가 실리지 않는다.
    fun getJoinedParticipants(roomId: UUID): List<JoinedParticipant> = participationFinder.getJoinedParticipants(roomId)

    // 나가기(「룸 참여」 §4.6). 참여 여부 판정이 나가기 규칙과 한 잠금 안에 있어야 해서
    // 여기서 validateParticipant 를 먼저 부르지 않는다 — Manager 가 룸을 잠근 뒤 함께 본다.
    fun leave(memberId: UUID, roomId: UUID) {
        roomLeaveManager.leave(roomId, memberId)
    }
}
