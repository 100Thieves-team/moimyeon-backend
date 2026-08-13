package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import org.springframework.stereotype.Component
import java.util.UUID

// 원본 열람의 발급 시점 재검증(MOI-414). URL 을 따 둬도 매 발급마다 여기서 다시 판정된다.
// 뷰어 게이트(E1419)는 ParticipationValidator 가 이보다 먼저 본다 - 제3자에게 룸 상태를 흘리지 않는다.
@Component
class ResumeOriginalViewFinder(
    private val roomFinder: RoomFinder,
    private val participationFinder: ParticipationFinder,
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
) {
    fun getViewableFile(roomId: UUID, resumeSubmissionId: Long): ResumeFile {
        val room = roomFinder.getRoom(roomId)
        requireBusiness(room.opensResumeOriginal(), CoreErrorType.RESUME_ORIGINAL_NOT_VIEWABLE)

        val submission = requireFound(
            resumeSubmissionRepository.findByIdAndRoomIdAndDeletedAtIsNull(resumeSubmissionId, roomId),
            CoreErrorType.RESUME_NOT_FOUND,
        )
        // 이탈·강퇴된 제출자의 원본은 회수된다(「참여」 - LEFT 시 원본·요약 회수).
        requireBusiness(
            participationFinder.isParticipating(roomId, submission.memberId),
            CoreErrorType.RESUME_ORIGINAL_NOT_VIEWABLE,
        )

        return ResumeFile(
            key = submission.fileKey,
            originalName = submission.originalName,
            sizeBytes = submission.sizeBytes,
            contentType = submission.contentType,
        )
    }
}
