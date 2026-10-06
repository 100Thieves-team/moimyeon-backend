package io.plady.moimyeon.core.domain.progress

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.participation.RoomParticipantService
import io.plady.moimyeon.core.domain.room.FIXED_NOW
import io.plady.moimyeon.core.domain.room.FixedClockTestConfiguration
import io.plady.moimyeon.core.domain.room.RoomLeaveManager
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.OutboxRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import java.time.LocalDateTime
import java.util.UUID

// MOI-571 재현: 확정 후 참여자가 나가도 룸이 확정 상태로 남으면, 명부의 현재 명단으로는 완료할 수 없었다(E1706).
@Import(FixedClockTestConfiguration::class)
class RoomConfirmedRosterCompletionIT(
    private val roomLeaveManager: RoomLeaveManager,
    private val roomParticipantService: RoomParticipantService,
    private val roomProgressService: RoomProgressService,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val attendanceRepository: AttendanceRepository,
    private val outboxRepository: OutboxRepository,
    private val memberRepository: MemberRepository,
) : ContextTest() {
    private val roomId = UUID.randomUUID()
    private val hostMemberId = UUID.randomUUID()
    private val stayingMemberId = UUID.randomUUID()
    private val leavingMemberId = UUID.randomUUID()
    private val joinedAt: LocalDateTime = FIXED_NOW.minusDays(5)
    private val confirmedAt: LocalDateTime = FIXED_NOW.minusDays(1)
    private val startAt: LocalDateTime = FIXED_NOW.plusDays(7)

    @AfterEach
    fun cleanUp() {
        outboxRepository.deleteAll(outboxRepository.findAll().filter { roomId.toString() in it.payload })
        attendanceRepository.deleteAll(attendanceRepository.findAll().filter { it.roomId == roomId })
        roomStatusLogRepository.deleteAll(roomStatusLogRepository.findAll().filter { it.roomId == roomId })
        participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId == roomId })
        if (roomRepository.existsById(roomId)) roomRepository.deleteById(roomId)
        memberRepository.deleteAllById(listOf(hostMemberId, stayingMemberId, leavingMemberId))
    }

    @Test
    fun `확정 후 참여자가 나간 룸에서 명부의 확정 명단으로 완료하면 성공한다`() {
        seedConfirmedRoomOfThree()
        roomLeaveManager.leave(roomId, leavingMemberId)
        assertThat(roomRepository.findById(roomId).orElseThrow().status).isEqualTo(RoomStatus.CONFIRMED)

        val roster = roomParticipantService.getParticipants(hostMemberId, roomId)
        assertThat(roster.participants.map { it.memberId }).doesNotContain(leavingMemberId)
        // 고치기 전 화면이 쓰던 현재 명단으로는 완료할 수 없다.
        assertThatThrownBy {
            roomProgressService.complete(hostMemberId, roomId, roster.participants.map { attended(it.memberId) })
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PROGRESS_PARTICIPANT_MISMATCH)
        }

        roomProgressService.complete(hostMemberId, roomId, roster.confirmedParticipants.map { attended(it.memberId) })

        assertThat(roomRepository.findById(roomId).orElseThrow().status).isEqualTo(RoomStatus.COMPLETED)
        assertThat(attendanceRepository.findAllByRoomIdAndDeletedAtIsNullOrderByIdAsc(roomId).map { it.memberId })
            .containsExactlyInAnyOrder(hostMemberId, stayingMemberId, leavingMemberId)
    }

    private fun attended(memberId: UUID) = Attendance(memberId, AttendanceStatus.ATTENDED)

    // 최소 2명 룸을 3명으로 확정한다 - 1명이 나가도 최소 인원 이상이라 확정 상태가 유지된다.
    private fun seedConfirmedRoomOfThree() {
        val room = RoomEntity(
            id = roomId,
            jobPostingId = 1L,
            jobRoleId = 1L,
            resumePublic = false,
            sigunguId = null,
            title = "확정 명단 완료 테스트 룸",
            description = null,
            interviewStage = InterviewStage.FIRST,
            interviewType = InterviewType.JOB,
            meetingType = MeetingType.ONLINE,
            minCapacity = 2,
            maxCapacity = 6,
            startAt = startAt,
            durationMinutes = 60,
        )
        room.confirm()
        roomRepository.saveAndFlush(room)
        listOf(
            hostMemberId to ParticipationRole.HOST,
            stayingMemberId to ParticipationRole.PARTICIPANT,
            leavingMemberId to ParticipationRole.PARTICIPANT,
        ).forEachIndexed { index, (memberId, role) ->
            persistMember(memberId)
            participationRepository.saveAndFlush(
                ParticipationEntity(
                    roomId = roomId,
                    memberId = memberId,
                    participationRole = role,
                    status = ParticipationStatus.JOINED,
                    joinedAt = joinedAt.plusMinutes(index.toLong()),
                ),
            )
        }
        roomStatusLogRepository.saveAndFlush(
            RoomStatusLogEntity.byMember(
                roomId = roomId,
                transitionType = RoomStatus.CONFIRMED,
                handlerMemberId = hostMemberId,
                occurredAt = confirmedAt,
            ),
        )
    }

    private fun persistMember(memberId: UUID) {
        val suffix = memberId.toString().take(8)
        memberRepository.saveAndFlush(
            MemberEntity(
                id = memberId,
                email = "roster-$suffix@example.com",
                nickname = "명단$suffix",
                status = MemberStatus.ACTIVE,
                lastLoginAt = joinedAt,
                socialAccounts = listOf(
                    SocialAccountEntity(
                        provider = SocialLoginProvider.GOOGLE,
                        providerId = "roster-$suffix",
                        linkedEmail = "roster-$suffix@example.com",
                    ),
                ),
            ),
        )
    }
}
