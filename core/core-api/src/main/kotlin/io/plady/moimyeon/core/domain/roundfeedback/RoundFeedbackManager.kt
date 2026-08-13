package io.plady.moimyeon.core.domain.roundfeedback

import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.enums.RoundFeedbackType
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.RoomRepository
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
    private val roomRepository: RoomRepository,
    private val feedbackRepository: RoundFeedbackRepository,
    private val clock: Clock,
) {
    @Transactional
    fun registerFinalFeedback(command: RoundFeedbackCommand): Long {
        lockEditableRoom(command.roomId)
        if (findByAuthor(command) != null) {
            throw CoreException(CoreErrorType.ROUND_FEEDBACK_ALREADY_EXISTS)
        }

        return try {
            save(command, RoundFeedbackType.FINAL)
        } catch (exception: DataIntegrityViolationException) {
            if (exception.matchesConstraint(ROUND_AUTHOR_UNIQUE_CONSTRAINT)) {
                throw CoreException(CoreErrorType.ROUND_FEEDBACK_ALREADY_EXISTS)
            }
            throw exception
        }
    }

    @Transactional
    fun upsertSelfFeedback(command: RoundFeedbackCommand): Long {
        // 아직 없는 피드백 행은 잠글 수 없다. 항상 존재하는 룸 행을 먼저 잠가 같은 룸의 최초 INSERT를
        // 직렬화하면, 두 요청이 모두 existing == null을 보고 유니크 충돌로 가는 레이스가 사라진다.
        lockEditableRoom(command.roomId)
        val existing = findByAuthor(command)
        if (existing != null) {
            existing.edit(command.content)
            return existing.id
        }
        return save(command, RoundFeedbackType.SELF)
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

    private fun lockEditableRoom(roomId: UUID) {
        val room = requireFound(
            roomRepository.findByIdForUpdate(roomId)?.takeIf { it.isActive() },
            CoreErrorType.ROOM_NOT_FOUND,
        )
        requireBusiness(room.status == RoomStatus.IN_PROGRESS, CoreErrorType.ROUND_FEEDBACK_NOT_EDITABLE)
    }

    private fun findByAuthor(command: RoundFeedbackCommand): RoundFeedbackEntity? {
        return feedbackRepository
            .findForUpdateByRoomIdAndIntervieweeMemberIdAndAuthorMemberIdAndDeletedAtIsNull(
                command.roomId,
                command.intervieweeMemberId,
                command.authorMemberId,
            )
    }

    private fun save(command: RoundFeedbackCommand, type: RoundFeedbackType): Long {
        return feedbackRepository.saveAndFlush(
            RoundFeedbackEntity(
                roomId = command.roomId,
                intervieweeMemberId = command.intervieweeMemberId,
                authorMemberId = command.authorMemberId,
                feedbackType = type,
                content = command.content,
            ),
        ).id
    }

    private fun DataIntegrityViolationException.matchesConstraint(name: String): Boolean {
        return generateSequence<Throwable>(this) { it.cause }
            .mapNotNull { it.message }
            .any { it.contains(name, ignoreCase = true) }
    }

    private companion object {
        const val ROUND_AUTHOR_UNIQUE_CONSTRAINT = "uk_round_feedback_round_author_active"
    }
}
