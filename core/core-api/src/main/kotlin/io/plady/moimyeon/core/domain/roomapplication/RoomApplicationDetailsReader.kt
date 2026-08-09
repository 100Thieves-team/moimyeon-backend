package io.plady.moimyeon.core.domain.roomapplication

import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.resume.ResumeFinder
import io.plady.moimyeon.core.domain.resume.ResumeSummary
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class RoomApplicationDetailsReader(
    private val roomApplicationRepository: RoomApplicationRepository,
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
    private val memberFinder: MemberFinder,
    private val resumeFinder: ResumeFinder,
) {
    @Transactional(readOnly = true)
    fun getAllByRoom(roomId: UUID): List<RoomApplicationDetails> {
        val applications = roomApplicationRepository
            .findByRoomIdAndStatusNotAndDeletedAtIsNullOrderByAppliedAtAsc(
                roomId,
                RoomApplicationStatus.WITHDRAWN,
            )
        if (applications.isEmpty()) return emptyList()

        val applicationIds = applications.map { it.id }
        val submissionsByApplicationId = resumeSubmissionRepository
            .findByRoomApplicationIdInAndDeletedAtIsNull(applicationIds)
            .associateBy { it.roomApplicationId }
        val membersById = memberFinder
            .getAllByIds(applications.map { it.applicantMemberId }.distinct())
            .associateBy { it.id }
        val resumeSummariesById = resumeFinder.getSummaries(
            submissionsByApplicationId.values.map { it.sourceResumeId }.distinct(),
        )

        return applications.map { application ->
            val submission = checkNotNull(submissionsByApplicationId[application.id]) {
                "참가 신청에는 제출 이력서가 있어야 합니다. applicationId=${application.id}"
            }
            application.toDetails(
                applicant = membersById[application.applicantMemberId]?.let {
                    ApplicationApplicant.Active(it.id, it.nickname.value)
                } ?: ApplicationApplicant.Withdrawn(application.applicantMemberId),
                resumeSummary = checkNotNull(resumeSummariesById[submission.sourceResumeId]) {
                    "제출 이력서에는 요약 정보가 있어야 합니다. resumeId=${submission.sourceResumeId}"
                }.toApplicationSummary(),
            )
        }
    }

    private fun RoomApplicationEntity.toDetails(
        applicant: ApplicationApplicant,
        resumeSummary: ApplicationResumeSummary,
    ): RoomApplicationDetails {
        return RoomApplicationDetails(
            applicationId = id,
            applicant = applicant,
            note = note,
            resumeSummary = resumeSummary,
            status = status,
            appliedAt = appliedAt,
        )
    }

    private fun ResumeSummary.toApplicationSummary(): ApplicationResumeSummary {
        return when (status) {
            ResumeSummaryStatus.DONE -> ApplicationResumeSummary.Ready(checkNotNull(content))
            ResumeSummaryStatus.PROCESSING,
            ResumeSummaryStatus.FAILED,
            -> ApplicationResumeSummary.Preparing
        }
    }
}
