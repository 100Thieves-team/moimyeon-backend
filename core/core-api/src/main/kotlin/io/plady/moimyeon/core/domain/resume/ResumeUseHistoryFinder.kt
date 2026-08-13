package io.plady.moimyeon.core.domain.resume

import io.plady.moimyeon.core.domain.room.RoomFinder
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ResumeUseHistoryFinder(
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
    private val roomFinder: RoomFinder,
) {
    fun getLatest(memberId: UUID, resumeIds: Collection<UUID>): Map<UUID, ResumeLastUsed> {
        if (resumeIds.isEmpty()) return emptyMap()

        val latestSubmissions = resumeSubmissionRepository
            .findByMemberIdAndSourceResumeIdInAndDeletedAtIsNullOrderBySubmittedAtDescIdDesc(
                memberId,
                resumeIds,
            )
            .distinctBy { it.sourceResumeId }
        val roomsById = roomFinder
            .getAllByIds(latestSubmissions.map { it.roomId }.distinct())
            .associateBy { it.id }

        return latestSubmissions.associate { submission ->
            submission.sourceResumeId to ResumeLastUsed(
                roomId = submission.roomId,
                roomTitle = roomsById[submission.roomId]?.title?.value ?: DELETED_ROOM_TITLE,
                usedAt = submission.submittedAt,
            )
        }
    }

    private companion object {
        const val DELETED_ROOM_TITLE = "삭제된 면접"
    }
}
