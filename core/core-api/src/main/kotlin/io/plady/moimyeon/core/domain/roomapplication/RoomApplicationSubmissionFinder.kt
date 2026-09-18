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

    // 룸 목록의 뷰어 사실 조회용(MOI-500). 룸 수에 비례해 쿼리가 늘지 않게 한 번에 읽는다.
    // 한 룸에 이력이 여러 건일 수 있어(철회 후 재신청) 최신 1건만 관계를 말한다 —
    // 정렬이 최신순이므로 각 룸의 첫 행이다. 단건 경로(getLatestByApplicant)와 같은 규칙이라
    // 목록과 상세가 다른 관계를 말하지 않는다.
    fun getLatestStatusByRooms(
        applicantMemberId: UUID,
        roomIds: Collection<UUID>,
    ): Map<UUID, RoomApplicationStatus> {
        if (roomIds.isEmpty()) return emptyMap()

        return roomApplicationRepository
            .findByApplicantMemberIdAndRoomIdInAndDeletedAtIsNullOrderByAppliedAtDescIdDesc(
                applicantMemberId,
                roomIds,
            )
            .groupBy { it.roomId }
            .mapValues { (_, applications) -> applications.first().status }
    }

    // 막지 않고 숫자를 묻는다(MOI-500) — 판정은 화면과 신청 경로가 각자 한다. 막는 쪽은
    // RoomApplicationSubmissionManager 가 자기 커밋 경계 안에서 같은 집계로 갖는다.
    fun getPendingApplicationQuota(applicantMemberId: UUID): PendingApplicationQuota {
        return PendingApplicationQuota.of(
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
