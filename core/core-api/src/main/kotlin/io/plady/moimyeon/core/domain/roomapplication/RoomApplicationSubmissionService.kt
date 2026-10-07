package io.plady.moimyeon.core.domain.roomapplication

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.participation.ParticipationValidator
import io.plady.moimyeon.core.domain.resume.ResumeValidator
import org.springframework.stereotype.Service
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class RoomApplicationSubmissionService(
    private val roomApplicationSubmissionManager: RoomApplicationSubmissionManager,
    private val resumeValidator: ResumeValidator,
    private val roomApplicationSubmissionFinder: RoomApplicationSubmissionFinder,
    private val participationValidator: ParticipationValidator,
    private val roomApplicationDetailsReader: RoomApplicationDetailsReader,
) {
    fun submit(
        applicantMemberId: UUID,
        roomId: UUID,
        applicationForm: RoomApplicationForm,
    ): Long {
        log.debug { "room-application.submit memberId=$applicantMemberId roomId=$roomId" }
        val submittedFile = resumeValidator.validateOwnedBy(applicantMemberId, applicationForm.resumeId)
        return roomApplicationSubmissionManager.submit(
            applicantMemberId,
            roomId,
            applicationForm.note,
            ResumeSubmission(applicationForm.resumeId, submittedFile),
        )
    }

    fun getLatestApplication(applicantMemberId: UUID, roomId: UUID): RoomApplication {
        return roomApplicationSubmissionFinder.getLatestByApplicant(applicantMemberId, roomId)
    }

    fun getPendingApplications(applicantMemberId: UUID): List<PendingRoomApplication> {
        return roomApplicationSubmissionFinder.getPendingByApplicant(applicantMemberId)
    }

    fun getApplications(hostMemberId: UUID, roomId: UUID): List<RoomApplicationDetails> {
        participationValidator.validateHost(roomId, hostMemberId)
        return roomApplicationDetailsReader.getAllByRoom(roomId)
    }

    fun withdraw(applicantMemberId: UUID, roomId: UUID) {
        log.debug { "room-application.withdraw memberId=$applicantMemberId roomId=$roomId" }
        roomApplicationSubmissionManager.withdraw(applicantMemberId, roomId)
    }
}
