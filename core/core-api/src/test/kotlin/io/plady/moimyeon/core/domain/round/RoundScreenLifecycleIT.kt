package io.plady.moimyeon.core.domain.round

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.QuestionEntity
import io.plady.moimyeon.storage.db.core.QuestionRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class RoundScreenLifecycleIT(
    private val roundService: RoundService,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val attendanceRepository: AttendanceRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val questionRepository: QuestionRepository,
) : ContextTest() {
    private val roomId = UUID.randomUUID()
    private val firstMemberId = UUID.randomUUID()
    private val noShowMemberId = UUID.randomUUID()
    private val thirdMemberId = UUID.randomUUID()
    private val confirmedAt = LocalDateTime.of(2026, 8, 13, 10, 0)
    private val startedAt = confirmedAt.plusHours(1)

    @Test
    fun `3인 룸에서 라운드가 바뀌면 면접자 가림이 뒤집히고 노쇼도 활동 화면을 본다`() {
        seedInProgressRoom()
        val firstQuestion = questionRepository.saveAndFlush(
            question(
                targetMemberId = firstMemberId,
                authorMemberId = noShowMemberId,
                content = "첫 번째 참여자 질문",
            ),
        )
        val noShowQuestion = questionRepository.saveAndFlush(
            question(
                targetMemberId = noShowMemberId,
                authorMemberId = firstMemberId,
                content = "노쇼 참여자 질문",
            ),
        )

        val firstMemberInOwnRound = roundService.getScreen(firstMemberId, roomId, firstMemberId)
        val noShowInFirstRound = roundService.getScreen(noShowMemberId, roomId, firstMemberId)
        val firstMemberInNoShowRound = roundService.getScreen(firstMemberId, roomId, noShowMemberId)
        val noShowInOwnRound = roundService.getScreen(noShowMemberId, roomId, noShowMemberId)
        val thirdMemberInNoShowRound = roundService.getScreen(thirdMemberId, roomId, noShowMemberId)

        assertThat(firstMemberInOwnRound).isEqualTo(RoundScreen.Interviewee(firstMemberId))
        assertThat(noShowInOwnRound).isEqualTo(RoundScreen.Interviewee(noShowMemberId))
        assertParticipantScreen(noShowInFirstRound, firstMemberId, firstQuestion.id)
        assertParticipantScreen(firstMemberInNoShowRound, noShowMemberId, noShowQuestion.id)
        assertParticipantScreen(thirdMemberInNoShowRound, noShowMemberId, noShowQuestion.id)
    }

    private fun assertParticipantScreen(
        screen: RoundScreen,
        intervieweeMemberId: UUID,
        questionId: Long,
    ) {
        assertThat(screen).isInstanceOfSatisfying(RoundScreen.Participant::class.java) {
            assertThat(it.intervieweeMemberId).isEqualTo(intervieweeMemberId)
            assertThat(it.questionCardSet.targetMemberId).isEqualTo(intervieweeMemberId)
            assertThat(it.questionCardSet.questions.map { question -> question.id }).containsExactly(questionId)
        }
    }

    private fun seedInProgressRoom() {
        val room = RoomEntity(
            id = roomId,
            jobPostingId = 1L,
            jobRoleId = 1L,
            resumePublic = false,
            sigunguId = null,
            title = "라운드 화면 테스트 룸",
            description = null,
            interviewStage = InterviewStage.FIRST,
            interviewType = InterviewType.JOB,
            meetingType = MeetingType.ONLINE,
            minCapacity = 3,
            maxCapacity = 4,
            startAt = startedAt,
            durationMinutes = 60,
        )
        room.confirm()
        room.startProgress(startedAt)
        roomRepository.saveAndFlush(room)

        participationRepository.saveAllAndFlush(
            listOf(
                participation(firstMemberId, ParticipationRole.HOST, confirmedAt.minusDays(3)),
                participation(noShowMemberId, ParticipationRole.PARTICIPANT, confirmedAt.minusDays(2)),
                participation(thirdMemberId, ParticipationRole.PARTICIPANT, confirmedAt.minusDays(1)),
            ),
        )
        roomStatusLogRepository.saveAndFlush(
            RoomStatusLogEntity.byMember(
                roomId = roomId,
                transitionType = RoomStatus.CONFIRMED,
                handlerMemberId = firstMemberId,
                occurredAt = confirmedAt,
            ),
        )
        attendanceRepository.saveAllAndFlush(
            listOf(
                attendance(firstMemberId, AttendanceStatus.ATTENDED),
                attendance(noShowMemberId, AttendanceStatus.ABSENT),
                attendance(thirdMemberId, AttendanceStatus.ATTENDED),
            ),
        )
    }

    private fun participation(
        memberId: UUID,
        role: ParticipationRole,
        joinedAt: LocalDateTime,
    ): ParticipationEntity {
        return ParticipationEntity(
            roomId = roomId,
            memberId = memberId,
            participationRole = role,
            status = ParticipationStatus.JOINED,
            joinedAt = joinedAt,
        )
    }

    private fun attendance(memberId: UUID, status: AttendanceStatus): AttendanceEntity {
        return AttendanceEntity(
            roomId = roomId,
            memberId = memberId,
            status = status,
            recorderMemberId = firstMemberId,
            recordedAt = startedAt,
        )
    }

    private fun question(
        targetMemberId: UUID,
        authorMemberId: UUID,
        content: String,
    ): QuestionEntity {
        return QuestionEntity(
            roomId = roomId,
            targetMemberId = targetMemberId,
            authorMemberId = authorMemberId,
            content = content,
            source = QuestionSource.PREPARATION,
        )
    }
}
