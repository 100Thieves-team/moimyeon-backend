package io.plady.moimyeon.core.domain.trust

import io.plady.moimyeon.storage.db.core.ReviewSkipEntity
import io.plady.moimyeon.storage.db.core.ReviewSkipRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

private const val REVIEW_SKIP_UNIQUE_CONSTRAINT = "uk_review_skip_room_author_target"

@Component
class ReviewSkipRecorder(
    private val skipRepository: ReviewSkipRepository,
) {
    fun record(command: ReviewSkipCommand) {
        if (skipRepository.existsByRoomIdAndAuthorMemberIdAndTargetMemberId(
                command.roomId,
                command.authorMemberId,
                command.targetMemberId,
            )
        ) {
            return
        }

        try {
            skipRepository.saveAndFlush(
                ReviewSkipEntity(
                    roomId = command.roomId,
                    authorMemberId = command.authorMemberId,
                    targetMemberId = command.targetMemberId,
                ),
            )
        } catch (exception: DataIntegrityViolationException) {
            if (!isDuplicatedSkip(exception)) throw exception
        }
    }

    private fun isDuplicatedSkip(exception: DataIntegrityViolationException): Boolean {
        return (exception.rootCause?.message ?: exception.message)
            .orEmpty()
            .contains(REVIEW_SKIP_UNIQUE_CONSTRAINT, ignoreCase = true)
    }
}
