package io.plady.moimyeon.core.domain.roomapplication

import io.plady.moimyeon.core.domain.member.MemberValidator
import io.plady.moimyeon.core.domain.participation.ParticipationValidator
import io.plady.moimyeon.core.domain.room.RoomValidator
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.error.requireBusiness
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
private const val MAX_PENDING_ROOM_APPLICATION_COUNT = 3L

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
        note: String?,
        resumeSubmission: ResumeSubmission,
    ): Long {
        memberValidator.validateActive(applicantMemberId)
        validatePendingApplicationCount(applicantMemberId)

        roomValidator.validateAcceptingApplications(roomId)
        validateApplicant(roomId, applicantMemberId)

        val now = LocalDateTime.now(clock)
        val application = saveApplication(roomId, applicantMemberId, note, now)
        resumeSubmissionRepository.save(
            ResumeSubmissionEntity(
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

    private fun validatePendingApplicationCount(applicantMemberId: UUID) {
        val pendingApplicationCount =
            roomApplicationRepository.countByApplicantMemberIdAndStatusAndDeletedAtIsNull(
                applicantMemberId,
                RoomApplicationStatus.PENDING,
            )
        requireBusiness(
            pendingApplicationCount < MAX_PENDING_ROOM_APPLICATION_COUNT,
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
        note: String?,
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
