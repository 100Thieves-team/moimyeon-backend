package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RoomApplicationFinder(
    private val roomRepository: RoomRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val participationRepository: ParticipationRepository,
    private val memberRepository: MemberRepository,
) {
    // 방장용 참가 신청 목록(「룸 참여」 §4.3). 대기 신청자 목록은 방장 외 비공개이므로(§6) 방장만 조회한다.
    // 철회 신청은 제외하고, 신청자 닉네임은 한 번에 조회해 붙인다(직무·활동·AI 요약 원천은 아직 없어 표현 계층이 null).
    fun getApplications(roomId: UUID, requesterMemberId: UUID): List<RoomApplicationView> {
        requireFound(
            roomRepository.findById(roomId).orElse(null)?.takeIf { it.isActive() },
            CoreErrorType.ROOM_NOT_FOUND,
        )
        requireBusiness(
            participationRepository.existsByRoomIdAndMemberIdAndParticipationRoleAndDeletedAtIsNull(
                roomId,
                requesterMemberId,
                ParticipationRole.HOST,
            ),
            CoreErrorType.ROOM_FORBIDDEN,
        )

        val applications = roomApplicationRepository
            .findByRoomIdAndStatusNotAndDeletedAtIsNullOrderByAppliedAtAsc(roomId, RoomApplicationStatus.WITHDRAWN)
        if (applications.isEmpty()) return emptyList()

        val nicknamesById = memberRepository.findAllById(applications.map { it.applicantMemberId })
            .associate { it.id to it.nickname }

        return applications.map { application ->
            RoomApplicationView(
                applicationId = application.id,
                applicantMemberId = application.applicantMemberId,
                applicantNickname = nicknamesById[application.applicantMemberId] ?: UNKNOWN_NICKNAME,
                note = application.note,
                status = application.status,
                appliedAt = application.appliedAt,
            )
        }
    }

    companion object {
        // 신청자 회원이 조회되지 않는 예외 상황(탈퇴 등)의 표시값.
        private const val UNKNOWN_NICKNAME = "알 수 없음"
    }
}
