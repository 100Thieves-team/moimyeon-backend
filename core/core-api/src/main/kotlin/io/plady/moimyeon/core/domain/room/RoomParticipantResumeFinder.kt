package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.core.domain.resume.ResumeFinder
import io.plady.moimyeon.core.domain.resume.ResumeSummary
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ResumeSubmissionEntity
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import org.springframework.stereotype.Component
import java.util.UUID

// 룸 참여자가 그 룸에 낸 이력서. 제출 시점 사본을 가리키므로 회원이 나중에 원본을 바꾸거나 숨겨도
// "무엇을 냈는가" 는 유지된다. 요약 본문만 resume 를 따라간다.
@Component
class RoomParticipantResumeFinder(
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
    private val resumeFinder: ResumeFinder,
) {
    fun get(roomId: UUID, participantMemberId: UUID): RoomParticipantResume {
        val submission = requireFound(
            resumeSubmissionRepository.findByRoomIdAndMemberIdAndDeletedAtIsNull(roomId, participantMemberId),
            CoreErrorType.RESUME_NOT_FOUND,
        )
        val summaries = resumeFinder.getSummaries(listOf(submission.sourceResumeId))
        return submission.toParticipantResume(summaries)
    }

    // 참여자 명부용 일괄 조회. 참여자 수에 비례해 쿼리가 늘지 않게 두 번으로 끝낸다.
    fun getAllByRoom(roomId: UUID): Map<UUID, RoomParticipantResume> {
        val submissions = resumeSubmissionRepository.findByRoomIdAndDeletedAtIsNull(roomId)
        if (submissions.isEmpty()) return emptyMap()

        val summaries = resumeFinder.getSummaries(submissions.map { it.sourceResumeId }.distinct())
        return submissions.associate { it.memberId to it.toParticipantResume(summaries) }
    }

    private fun ResumeSubmissionEntity.toParticipantResume(
        summaries: Map<UUID, ResumeSummary>,
    ): RoomParticipantResume {
        return RoomParticipantResume(
            roomId = roomId,
            participantMemberId = memberId,
            submissionId = id,
            sourceResumeId = sourceResumeId,
            summary = checkNotNull(summaries[sourceResumeId]) {
                "제출 이력서에는 요약 정보가 있어야 합니다. resumeId=$sourceResumeId"
            },
        )
    }
}
