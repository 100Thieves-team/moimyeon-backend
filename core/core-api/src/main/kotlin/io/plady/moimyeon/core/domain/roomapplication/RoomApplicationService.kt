package io.plady.moimyeon.core.domain.roomapplication

import io.plady.moimyeon.core.domain.participation.ParticipationValidator
import io.plady.moimyeon.core.domain.resume.ResumeValidator
import org.springframework.stereotype.Service
import java.util.UUID

@Service("applicantRoomApplicationService")
class RoomApplicationService(
    private val roomApplicationManager: RoomApplicationManager,
    private val resumeValidator: ResumeValidator,
    private val roomApplicationFinder: RoomApplicationFinder,
    private val participationValidator: ParticipationValidator,
    private val roomApplicationDetailsReader: RoomApplicationDetailsReader,
) {
    fun submit(
        applicantMemberId: UUID,
        roomId: UUID,
        applicationForm: RoomApplicationForm,
    ): Long {
        val submittedFile = resumeValidator.validateOwnedBy(applicantMemberId, applicationForm.resumeId)
        return roomApplicationManager.submit(
            applicantMemberId,
            roomId,
            applicationForm.note,
            ResumeSubmission(applicationForm.resumeId, submittedFile),
        )
    }

    fun getMyApplication(applicantMemberId: UUID, roomId: UUID): RoomApplication {
        return roomApplicationFinder.getLatestByApplicant(applicantMemberId, roomId)
    }

    fun getApplications(hostMemberId: UUID, roomId: UUID): List<RoomApplicationDetails> {
        participationValidator.validateHost(roomId, hostMemberId)
        return roomApplicationDetailsReader.getAllByRoom(roomId)
    }

    fun withdraw(applicantMemberId: UUID, roomId: UUID) {
        roomApplicationManager.withdraw(applicantMemberId, roomId)
    }
}
