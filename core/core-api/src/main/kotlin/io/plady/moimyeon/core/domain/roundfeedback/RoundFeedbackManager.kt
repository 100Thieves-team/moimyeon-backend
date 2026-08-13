package io.plady.moimyeon.core.domain.roundfeedback

import io.plady.moimyeon.core.enums.RoundFeedbackType
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.RoundFeedbackEntity
import io.plady.moimyeon.storage.db.core.RoundFeedbackRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Component
class RoundFeedbackManager(
    private val feedbackRepository: RoundFeedbackRepository,
    private val clock: Clock,
) {
    @Transactional
    fun save(command: RoundFeedbackCommand): Long {
        val existing = feedbackRepository
            .findForUpdateByRoomIdAndIntervieweeMemberIdAndAuthorMemberIdAndDeletedAtIsNull(
                command.roomId,
                command.intervieweeMemberId,
                command.authorMemberId,
            )
        if (existing != null) {
            if (command.type == RoundFeedbackType.FINAL) {
                throw CoreException(CoreErrorType.ROUND_FEEDBACK_ALREADY_EXISTS)
            }
            existing.edit(command.content)
            return existing.id
        }

        return try {
            feedbackRepository.saveAndFlush(
                RoundFeedbackEntity(
                    roomId = command.roomId,
                    intervieweeMemberId = command.intervieweeMemberId,
                    authorMemberId = command.authorMemberId,
                    feedbackType = command.type,
                    content = command.content,
                ),
            ).id
        } catch (exception: DataIntegrityViolationException) {
            if (command.type == RoundFeedbackType.FINAL) {
                throw CoreException(CoreErrorType.ROUND_FEEDBACK_ALREADY_EXISTS)
            }
            throw exception
        }
    }

    @Transactional
    fun confirmDisclosure(
        roomId: UUID,
        intervieweeMemberId: UUID,
        feedbackId: Long,
    ) {
        val feedback = requireFound(
            feedbackRepository.findForUpdateByRoomIdAndIntervieweeMemberIdAndIdAndFeedbackTypeAndDeletedAtIsNull(
                roomId,
                intervieweeMemberId,
                feedbackId,
                RoundFeedbackType.FINAL,
            ),
            CoreErrorType.ROUND_FEEDBACK_NOT_FOUND,
        )
        feedback.disclose(LocalDateTime.now(clock))
    }
}
