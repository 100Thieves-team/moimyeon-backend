package io.plady.moimyeon.core.domain.trust

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

private const val REVIEW_ACTIVE_UNIQUE_CONSTRAINT = "uk_review_room_author_target_active"

@Component
class ReviewSubmissionManager(
    private val reviewRepository: ReviewRepository,
    private val roomRepository: RoomRepository,
    private val attendanceRepository: AttendanceRepository,
    private val eligibilityValidator: ReviewEligibilityValidator,
    private val clock: Clock,
) {
    @Transactional
    fun submit(command: ReviewSubmissionCommand): Long {
        val room = lockRoom(command)
        val authorAttendance = lockAttendance(command.roomId, command.authorMemberId)
        val targetAttendance = if (command.targetMemberId == command.authorMemberId) {
            null
        } else {
            lockAttendance(command.roomId, command.targetMemberId)
        }
        eligibilityValidator.validate(
            roomStatus = room.status,
            authorMemberId = command.authorMemberId,
            targetMemberId = command.targetMemberId,
            authorAttendanceStatus = authorAttendance?.status,
            targetAttendanceStatus = targetAttendance?.status,
        )
        requireBusiness(
            !reviewRepository.existsByRoomIdAndAuthorMemberIdAndTargetMemberIdAndDeletedAtIsNull(
                command.roomId,
                command.authorMemberId,
                command.targetMemberId,
            ),
            CoreErrorType.REVIEW_DUPLICATED,
        )

        return save(command, LocalDateTime.now(clock)).id
    }

    private fun lockRoom(command: ReviewSubmissionCommand) = requireFound(
        roomRepository.findByIdForUpdate(command.roomId)?.takeIf { it.isActive() },
        CoreErrorType.ROOM_NOT_FOUND,
    )

    private fun lockAttendance(roomId: UUID, memberId: UUID) = attendanceRepository
        .findForUpdateByRoomIdAndMemberIdAndDeletedAtIsNull(roomId, memberId)

    private fun save(command: ReviewSubmissionCommand, submittedAt: LocalDateTime): ReviewEntity {
        try {
            return reviewRepository.saveAndFlush(
                ReviewEntity(
                    roomId = command.roomId,
                    authorMemberId = command.authorMemberId,
                    targetMemberId = command.targetMemberId,
                    content = command.content,
                    visibleAt = submittedAt.plusHours(REVIEW_VISIBILITY_DELAY_HOURS),
                    tags = command.tags,
                ),
            )
        } catch (exception: DataIntegrityViolationException) {
            if (isActiveReviewConflict(exception)) {
                throw CoreException(CoreErrorType.REVIEW_DUPLICATED)
            }
            throw exception
        }
    }

    private fun isActiveReviewConflict(exception: DataIntegrityViolationException): Boolean {
        return (exception.rootCause?.message ?: exception.message)
            .orEmpty()
            .contains(REVIEW_ACTIVE_UNIQUE_CONSTRAINT, ignoreCase = true)
    }

    private companion object {
        const val REVIEW_VISIBILITY_DELAY_HOURS = 3L
    }
}
