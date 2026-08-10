package io.plady.moimyeon.core.domain.roomapplication

import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.domain.resume.ResumeFinder
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
