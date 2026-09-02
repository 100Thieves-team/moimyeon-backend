package io.plady.moimyeon.core.domain.participation

import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.core.domain.room.RoomParticipantResume
import io.plady.moimyeon.core.domain.room.RoomParticipantResumeFinder
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// 참여자 명부(「룸 참여」 §4.5). 조회는 전부 일괄이라 참여자 수에 비례해 쿼리가 늘지 않는다.
@Component
class RoomParticipantReader(
    private val participationRepository: ParticipationRepository,
    private val memberFinder: MemberFinder,
    private val participationFinder: ParticipationFinder,
    private val roomParticipantResumeFinder: RoomParticipantResumeFinder,
    private val roomFinder: RoomFinder,
) {
    @Transactional(readOnly = true)
    fun getAllByRoom(roomId: UUID, viewerMemberId: UUID): List<RoomParticipant> {
        val participations = participationRepository
            .findByRoomIdAndStatusAndDeletedAtIsNullOrderByJoinedAtAscIdAsc(roomId, ParticipationStatus.JOINED)
        if (participations.isEmpty()) return emptyList()

        val canViewOriginal = canViewOriginal(roomId, viewerMemberId)
        val nicknames = memberFinder.getAllByIds(participations.map { it.memberId })
            .associate { it.id to it.nickname.value }
        val resumes = roomParticipantResumeFinder.getAllByRoom(roomId)

        return participations
            .map { it.toParticipant(nicknames[it.memberId], resumes[it.memberId], canViewOriginal) }
            // 방장을 맨 앞으로. 정렬이 안정적이라 나머지는 쿼리의 참여 순서를 유지한다.
            .sortedBy { !it.isHost }
    }

    // 열람 창(Room.opensResumeOriginal)과 뷰어가 확정 참여자인지의 AND 다. 대상자별로 갈리지 않는다(§4.3·§4.5).
    // 창 판정은 발급 게이트(MOI-414)와 공유한다 - 여기가 갈리면 버튼은 없는데 발급은 되는 화면이 나온다.
    private fun canViewOriginal(roomId: UUID, viewerMemberId: UUID): Boolean {
        val room = roomFinder.getRoom(roomId)
        return room.opensResumeOriginal() &&
            participationFinder.wasConfirmedParticipant(roomId, viewerMemberId)
    }

    private fun ParticipationEntity.toParticipant(
        nickname: String?,
        resume: RoomParticipantResume?,
        canViewOriginal: Boolean,
    ): RoomParticipant {
        return RoomParticipant(
            memberId = memberId,
            nickname = nickname,
            isHost = participationRole == ParticipationRole.HOST,
            joinedAt = joinedAt,
            resumeSummary = resume?.summary,
            resumeSubmissionId = resume?.submissionId,
            // 제출 이력서가 없으면 열 원본도 없다(방장은 아직 제출 행이 없다 - MOI-333).
            canViewOriginal = canViewOriginal && resume != null,
        )
    }
}
