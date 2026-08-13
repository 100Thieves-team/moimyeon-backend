package io.plady.moimyeon.core.domain.roomapplication

import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.domain.resume.ResumeFinder
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RoomApplicationSubmissionFinder(
    private val roomApplicationRepository: RoomApplicationRepository,
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
    private val resumeFinder: ResumeFinder,
) {
    fun getPendingByApplicant(applicantMemberId: UUID): List<PendingRoomApplication> {
        val applications = roomApplicationRepository
            .findByApplicantMemberIdAndStatusAndDeletedAtIsNullOrderByAppliedAtDescIdDesc(
                applicantMemberId,
                RoomApplicationStatus.PENDING,
            )
        if (applications.isEmpty()) return emptyList()

        val submissionsByApplicationId = resumeSubmissionRepository
            .findByRoomApplicationIdInAndDeletedAtIsNull(applications.map { it.id })
            .associateBy { it.roomApplicationId }

        return applications.map { application ->
            val submission = checkNotNull(submissionsByApplicationId[application.id]) {
                "참가 신청에는 제출 이력서가 있어야 합니다. applicationId=${application.id}"
            }
            PendingRoomApplication(
                id = application.id,
                roomId = application.roomId,
                resumeOriginalName = submission.originalName,
                appliedAt = application.appliedAt,
            )
        }
    }

    // 막지 않고 묻는다 — ParticipationFinder.hasAvailableSlot 과 같은 성격이다.
    // 룸 상세(MOI-387)는 예외 없이 차단 사유만 표시해야 한다. 막는 쪽은
    // RoomApplicationSubmissionManager 가 자기 커밋 경계 안에서 갖는다.
    fun hasAvailableQuota(applicantMemberId: UUID): Boolean {
        return RoomApplicationQuota.isAvailable(
            roomApplicationRepository.countByApplicantMemberIdAndStatusAndDeletedAtIsNull(
                applicantMemberId,
                RoomApplicationStatus.PENDING,
            ),
        )
    }

    fun getLatestByApplicant(applicantMemberId: UUID, roomId: UUID): RoomApplication {
        val application = requireFound(
            roomApplicationRepository
                .findFirstByRoomIdAndApplicantMemberIdAndDeletedAtIsNullOrderByAppliedAtDescIdDesc(
                    roomId,
                    applicantMemberId,
                ),
            CoreErrorType.APPLICATION_NOT_FOUND,
        )
        val submission = checkNotNull(
            resumeSubmissionRepository.findByRoomApplicationIdAndDeletedAtIsNull(application.id),
        ) {
            "참가 신청에는 제출 이력서가 있어야 합니다. applicationId=${application.id}"
        }

        return RoomApplication(
            id = application.id,
            roomId = application.roomId,
            applicantMemberId = application.applicantMemberId,
            note = application.note,
            resumeSubmission = ResumeSubmission(
                sourceResumeId = submission.sourceResumeId,
                file = ResumeFile(
                    key = submission.fileKey,
                    originalName = submission.originalName,
                    sizeBytes = submission.sizeBytes,
                    contentType = submission.contentType,
                ),
            ),
            resumeSummary = resumeFinder.getSummary(applicantMemberId, submission.sourceResumeId),
            status = application.status,
            appliedAt = application.appliedAt,
        )
    }
}
