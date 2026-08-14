package io.plady.moimyeon.core.domain.closing

import io.plady.moimyeon.core.domain.progress.RoomProgressReader
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ClosingQuestionRepository
import io.plady.moimyeon.storage.db.core.ClosingResponseEntity
import io.plady.moimyeon.storage.db.core.ClosingResponseRepository
import io.plady.moimyeon.storage.db.core.QuestionVoteEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ClosingSubmissionManager(
    private val roomRepository: RoomRepository,
    private val roomProgressReader: RoomProgressReader,
    private val closingQuestionRepository: ClosingQuestionRepository,
    private val closingResponseRepository: ClosingResponseRepository,
) {
    @Transactional
    fun submit(command: ClosingSubmissionCommand): ClosingSubmission {
        val room = requireFound(
            roomRepository.findByIdForUpdate(command.roomId)?.takeIf { it.isActive() },
            CoreErrorType.ROOM_NOT_FOUND,
        )
        closingResponseRepository.findByRoomIdAndMemberIdAndDeletedAtIsNull(
            command.roomId,
            command.memberId,
        )?.let { return it.toSubmission() }

        requireBusiness(room.status == RoomStatus.IN_PROGRESS, CoreErrorType.CLOSING_NOT_AVAILABLE)
        requireBusiness(
            roomProgressReader.isAttended(command.roomId, command.memberId),
            CoreErrorType.CLOSING_SUBMISSION_FORBIDDEN,
        )

        val questionIds = closingQuestionRepository.findAllAskedTopLevelByRoomIdAndTargetMemberId(
            roomId = command.roomId,
            targetMemberId = command.memberId,
        ).map { it.id }
        val submittedQuestionIds = command.evaluations.map(QuestionEvaluation::questionId)
        requireBusiness(
            submittedQuestionIds.size == questionIds.size && submittedQuestionIds.toSet() == questionIds.toSet(),
            CoreErrorType.CLOSING_QUESTION_MISMATCH,
        )

        val response = closingResponseRepository.saveAndFlush(
            ClosingResponseEntity(
                roomId = command.roomId,
                memberId = command.memberId,
                questionVotes = command.evaluations.map {
                    QuestionVoteEntity(
                        questionId = it.questionId,
                        vote = it.vote,
                    )
                },
            ),
        )
        return response.toSubmission()
    }

    private fun ClosingResponseEntity.toSubmission(): ClosingSubmission {
        return ClosingSubmission(
            roomId = roomId,
            memberId = memberId,
            submittedAt = createdAt,
        )
    }
}
