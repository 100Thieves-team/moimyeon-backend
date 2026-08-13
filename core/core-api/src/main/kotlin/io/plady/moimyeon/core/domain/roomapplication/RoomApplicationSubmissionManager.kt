package io.plady.moimyeon.core.domain.roomapplication

import io.plady.moimyeon.core.domain.member.MemberValidator
import io.plady.moimyeon.core.domain.participation.ParticipationValidator
import io.plady.moimyeon.core.domain.room.RoomValidator
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ResumeSubmissionEntity
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

private const val ROOM_APPLICATION_PENDING_UNIQUE_CONSTRAINT = "uk_room_application_room_pending_active"

@Component
class RoomApplicationSubmissionManager(
    private val memberValidator: MemberValidator,
    private val roomValidator: RoomValidator,
    private val participationValidator: ParticipationValidator,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
    private val clock: Clock,
) {
    @Transactional
    fun submit(
        applicantMemberId: UUID,
        roomId: UUID,
        note: String,
        resumeSubmission: ResumeSubmission,
    ): Long {
        // 회원 축 판정 둘을 나란히 둔다. 참여 슬롯을 앞에 두는 이유는 더 근본적인 제한이기 때문이다 —
        // 둘 다 초과면 "참여 중인 룸을 정리하라"가 신청자에게 더 정확한 안내다(MOI-427 D8).
        memberValidator.validateActive(applicantMemberId)
        participationValidator.validateSlotAvailable(applicantMemberId)
        validatePendingApplicationCount(applicantMemberId)

        roomValidator.validateAcceptingApplications(roomId)
        validateApplicant(roomId, applicantMemberId)

        val now = LocalDateTime.now(clock)
        val application = saveApplication(roomId, applicantMemberId, note, now)
        resumeSubmissionRepository.save(
            ResumeSubmissionEntity(
                roomApplicationId = application.id,
                roomId = roomId,
                memberId = applicantMemberId,
                sourceResumeId = resumeSubmission.sourceResumeId,
                fileKey = resumeSubmission.file.key,
                originalName = resumeSubmission.file.originalName,
                sizeBytes = resumeSubmission.file.sizeBytes,
                contentType = resumeSubmission.file.contentType,
                submittedAt = now,
            ),
        )
        return application.id
    }

    @Transactional
    fun withdraw(applicantMemberId: UUID, roomId: UUID) {
        val application = requireFound(
            roomApplicationRepository
                .findFirstForUpdateByRoomIdAndApplicantMemberIdAndDeletedAtIsNullOrderByAppliedAtDescIdDesc(
                    roomId,
                    applicantMemberId,
                ),
            CoreErrorType.APPLICATION_NOT_FOUND,
        )
        requireBusiness(application.isPending(), CoreErrorType.APPLICATION_ALREADY_HANDLED)

        application.withdraw(LocalDateTime.now(clock))
    }

    private fun validatePendingApplicationCount(applicantMemberId: UUID) {
        val pendingApplicationCount =
            roomApplicationRepository.countByApplicantMemberIdAndStatusAndDeletedAtIsNull(
                applicantMemberId,
                RoomApplicationStatus.PENDING,
            )
        requireBusiness(
            RoomApplicationQuota.isAvailable(pendingApplicationCount),
            CoreErrorType.ROOM_APPLICATION_PENDING_LIMIT_EXCEEDED,
        )
    }

    private fun validateApplicant(roomId: UUID, applicantMemberId: UUID) {
        participationValidator.validateNotHost(roomId, applicantMemberId)
        participationValidator.validateNotParticipating(roomId, applicantMemberId)
        requireBusiness(
            !roomApplicationRepository.existsByRoomIdAndPendingMemberIdAndDeletedAtIsNull(roomId, applicantMemberId),
            CoreErrorType.ROOM_APPLICATION_DUPLICATED,
        )
        requireBusiness(
            !roomApplicationRepository.existsByRoomIdAndApplicantMemberIdAndStatusAndDeletedAtIsNull(
                roomId,
                applicantMemberId,
                RoomApplicationStatus.REJECTED,
            ),
            CoreErrorType.ROOM_REAPPLICATION_NOT_ALLOWED,
        )
        participationValidator.validateNoRemovalHistory(roomId, applicantMemberId)
    }

    private fun saveApplication(
        roomId: UUID,
        applicantMemberId: UUID,
        note: String,
        appliedAt: LocalDateTime,
    ): RoomApplicationEntity {
        try {
            return roomApplicationRepository.saveAndFlush(
                RoomApplicationEntity(
                    roomId = roomId,
                    applicantMemberId = applicantMemberId,
                    note = note,
                    appliedAt = appliedAt,
                    status = RoomApplicationStatus.PENDING,
                    pendingMemberId = applicantMemberId,
                ),
            )
        } catch (exception: DataIntegrityViolationException) {
            if (isPendingApplicationConflict(exception)) {
                throw CoreException(CoreErrorType.ROOM_APPLICATION_DUPLICATED)
            }
            throw exception
        }
    }

    private fun isPendingApplicationConflict(exception: DataIntegrityViolationException): Boolean {
        return (exception.rootCause?.message ?: exception.message)
            .orEmpty()
            .contains(ROOM_APPLICATION_PENDING_UNIQUE_CONSTRAINT, ignoreCase = true)
    }
}
