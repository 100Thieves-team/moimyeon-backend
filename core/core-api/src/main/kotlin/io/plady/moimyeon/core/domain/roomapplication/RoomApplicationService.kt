package io.plady.moimyeon.core.domain.roomapplication

import io.plady.moimyeon.core.domain.resume.ResumeValidator
import org.springframework.stereotype.Service
import java.util.UUID

@Service("applicantRoomApplicationService")
class RoomApplicationService(
    private val roomApplicationManager: RoomApplicationManager,
    private val resumeValidator: ResumeValidator,
    private val roomApplicationFinder: RoomApplicationFinder,
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

    fun get(applicantMemberId: UUID, roomId: UUID): RoomApplication {
        return roomApplicationFinder.get(applicantMemberId, roomId)
    }

    fun withdraw(applicantMemberId: UUID, roomId: UUID) {
        roomApplicationManager.withdraw(applicantMemberId, roomId)
    }
}
