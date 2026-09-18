package io.plady.moimyeon.core.domain.closing

import io.plady.moimyeon.core.domain.progress.Attendance
import io.plady.moimyeon.core.domain.progress.RoomProgressReader
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.ClosingQuestionRepository
import io.plady.moimyeon.storage.db.core.ClosingResponseEntity
import io.plady.moimyeon.storage.db.core.ClosingResponseRepository
import io.plady.moimyeon.storage.db.core.QuestionVoteEntity
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ClosingSubmissionManager(
    private val roomRepository: RoomRepository,
    private val roomProgressReader: RoomProgressReader,
    private val closingQuestionRepository: ClosingQuestionRepository,
    private val closingResponseRepository: ClosingResponseRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
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
        completeRoomIfAllAttendedSubmitted(room, command, response)
        return response.toSubmission()
    }

    // 출석 참여자 전원이 제출하면 룸을 종료한다(PRD 「룸 진행」 §4.7, MOI-469).
    // 위에서 잡은 룸 행 락 안이라 마지막 두 명이 동시에 제출해도 전이는 한 번이다 —
    // 이 판정을 락(트랜잭션) 밖으로 옮기면 그 보장이 깨진다.
    private fun completeRoomIfAllAttendedSubmitted(
        room: RoomEntity,
        command: ClosingSubmissionCommand,
        response: ClosingResponseEntity,
    ) {
        val attendedMemberIds = roomProgressReader.getAttendances(command.roomId)
            .filter { it.status == AttendanceStatus.ATTENDED }
            .map(Attendance::memberId)
        val submittedMemberIds = closingResponseRepository.findAllByRoomIdAndDeletedAtIsNull(command.roomId)
            .map { it.memberId }
            .toSet()
        if (!submittedMemberIds.containsAll(attendedMemberIds)) return

        room.complete()
        roomStatusLogRepository.save(
            RoomStatusLogEntity.byMember(
                roomId = command.roomId,
                transitionType = RoomStatus.COMPLETED,
                handlerMemberId = command.memberId,
                occurredAt = response.createdAt,
            ),
        )
    }

    private fun ClosingResponseEntity.toSubmission(): ClosingSubmission {
        return ClosingSubmission(
            roomId = roomId,
            memberId = memberId,
            submittedAt = createdAt,
        )
    }
}
