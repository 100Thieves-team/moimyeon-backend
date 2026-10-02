package io.plady.moimyeon.core.domain.room

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.participation.JoinedParticipant
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.core.enums.EventType
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.event.OutboxEventPublisher
import io.plady.moimyeon.core.event.payload.RoomRecruitingReopenedEventPayload
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

// 나가기 규칙과 참여자 이탈의 모집 재개 판정을 본다. 방장 위임은 협력자가 많아 RoomLeaveIT 가 본다.
class RoomLeaveManagerTest {
    private val now = LocalDateTime.of(2026, 8, 12, 12, 0)

    private val roomRepository = mockk<RoomRepository>()
    private val participationRepository = mockk<ParticipationRepository>(relaxed = true)
    private val roomApplicationRepository = mockk<RoomApplicationRepository>(relaxed = true)
    private val roomStatusLogRepository = mockk<RoomStatusLogRepository> {
        every { save(any<RoomStatusLogEntity>()) } answers { firstArg() }
    }
    private val memberFinder = mockk<MemberFinder>(relaxed = true)
    private val participationFinder = mockk<ParticipationFinder>(relaxed = true)
    private val roomManager = mockk<RoomManager>(relaxed = true)
    private val outboxEventPublisher = mockk<OutboxEventPublisher>(relaxed = true)
    private val manager = RoomLeaveManager(
        roomRepository,
        participationRepository,
        roomApplicationRepository,
        roomStatusLogRepository,
        memberFinder,
        participationFinder,
        roomManager,
        outboxEventPublisher,
        Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
    )

    private val roomId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()
    private val hostMemberId = UUID.randomUUID()

    @Test
    fun `모집 중인 룸에서는 참여자가 자유롭게 나간다`() {
        givenRoom(RoomStatus.RECRUITING)
        val participation = givenParticipant()

        manager.leave(roomId, memberId)

        assertThat(participation.status).isEqualTo(ParticipationStatus.LEFT)
        assertThat(participation.leftAt).isEqualTo(now)
        assertThat(participation.leftByMemberId).isEqualTo(memberId)
    }

    @Test
    fun `확정된 룸도 인원이 최소보다 많으면 나갈 수 있다`() {
        givenRoom(RoomStatus.CONFIRMED, minCapacity = 3)
        val participation = givenParticipant(currentParticipants = 4)

        manager.leave(roomId, memberId)

        assertThat(participation.status).isEqualTo(ParticipationStatus.LEFT)
    }

    // 최소 인원은 나가기를 막지 않는다(구 E1423). 빠져서 최소 밑이 되면 모집으로 되돌린다.
    @Test
    fun `확정된 룸에서 인원이 최소와 같아도 참여자가 나가고 룸은 모집 중으로 돌아간다`() {
        val room = givenRoom(RoomStatus.CONFIRMED, minCapacity = 3)
        val participation = givenParticipant(currentParticipants = 3)

        manager.leave(roomId, memberId)

        assertThat(participation.status).isEqualTo(ParticipationStatus.LEFT)
        assertThat(room.status).isEqualTo(RoomStatus.RECRUITING)
        verify(exactly = 1) { roomStatusLogRepository.save(match { it.transitionType == RoomStatus.RECRUITING }) }
        verify(exactly = 1) {
            outboxEventPublisher.publish(EventType.ROOM_RECRUITING_REOPENED, any<RoomRecruitingReopenedEventPayload>())
        }
    }

    @Test
    fun `확정된 룸에서 참여자가 나가도 최소 인원 이상이 남으면 확정 상태가 유지된다`() {
        val room = givenRoom(RoomStatus.CONFIRMED, minCapacity = 3)
        givenParticipant(currentParticipants = 4)

        manager.leave(roomId, memberId)

        assertThat(room.status).isEqualTo(RoomStatus.CONFIRMED)
        assertNothingRecorded()
    }

    // 예정 시각과 같은 순간도 지난 것으로 본다(RoomSchedule.isPassed).
    @Test
    fun `진행 예정 시각이 지난 확정 룸에서는 참여자가 나가 최소 인원보다 적어져도 확정 상태가 유지된다`() {
        val room = givenRoom(RoomStatus.CONFIRMED, minCapacity = 3, startAt = now)
        val participation = givenParticipant(currentParticipants = 3)

        manager.leave(roomId, memberId)

        assertThat(participation.status).isEqualTo(ParticipationStatus.LEFT)
        assertThat(room.status).isEqualTo(RoomStatus.CONFIRMED)
        assertNothingRecorded()
    }

    @Test
    fun `진행 예정 시각 직전에 나가 최소 인원보다 적어지면 모집 중으로 돌아간다`() {
        val room = givenRoom(RoomStatus.CONFIRMED, minCapacity = 3, startAt = now.plusMinutes(1))
        givenParticipant(currentParticipants = 3)

        manager.leave(roomId, memberId)

        assertThat(room.status).isEqualTo(RoomStatus.RECRUITING)
    }

    @Test
    fun `모집 중인 룸에서 참여자가 나가면 상태가 바뀌지 않는다`() {
        val room = givenRoom(RoomStatus.RECRUITING, minCapacity = 3)
        givenParticipant(currentParticipants = 2)

        manager.leave(roomId, memberId)

        assertThat(room.status).isEqualTo(RoomStatus.RECRUITING)
        assertNothingRecorded()
    }

    // COMPLETED 는 메모리에서 만들 수 없다(전이가 없다). 판정을 "나갈 수 있는 상태" 화이트리스트로
    // 쓰면 여기 못 오는 상태도 함께 막힌다 — 그래서 열거가 아니라 화이트리스트여야 한다.
    @Test
    fun `완료되거나 취소된 룸에서는 나갈 수 없다`() {
        listOf(RoomStatus.COMPLETED, RoomStatus.CANCELED).forEach { status ->
            givenRoom(status)
            givenParticipant()

            assertThatThrownBy { manager.leave(roomId, memberId) }
                .describedAs("%s", status)
                .isInstanceOfSatisfying(CoreException::class.java) {
                    assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_ALREADY_CLOSED)
                }
        }
    }

    // 참여한 적이 없는 사람과 이미 나간 사람이 같은 결과다 — 둘 다 "지금 참여 중이 아니다".
    @Test
    fun `참여 중이 아니면 E1419 를 던진다`() {
        givenRoom(RoomStatus.RECRUITING)
        every {
            participationRepository.findByRoomIdAndMemberIdAndStatusAndDeletedAtIsNull(
                roomId,
                memberId,
                ParticipationStatus.JOINED,
            )
        } returns null

        assertThatThrownBy { manager.leave(roomId, memberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)
            }
    }

    @Test
    fun `탈퇴하면 모집 중이거나 확정된 룸에서 나간다`() {
        listOf(RoomStatus.RECRUITING, RoomStatus.CONFIRMED).forEach { status ->
            givenRoom(status, minCapacity = 2)
            val participation = givenParticipant(currentParticipants = 4)

            manager.leaveOnWithdrawal(roomId, memberId, now)

            assertThat(participation.status).describedAs("%s", status).isEqualTo(ParticipationStatus.LEFT)
        }
    }

    // 예정 시각과 같은 순간도 지난 것으로 본다(RoomSchedule.isPassed). 면접이 열렸을 수 있어 남긴다(R170).
    @Test
    fun `탈퇴해도 진행 예정 시각이 지난 확정 룸에서는 나가지 않는다`() {
        val room = givenRoom(RoomStatus.CONFIRMED, minCapacity = 3, startAt = now)
        val participation = givenParticipant(currentParticipants = 3)

        manager.leaveOnWithdrawal(roomId, memberId, now)

        assertThat(participation.status).isEqualTo(ParticipationStatus.JOINED)
        assertThat(room.status).isEqualTo(RoomStatus.CONFIRMED)
        assertNothingRecorded()
    }

    @Test
    fun `탈퇴해도 완료되거나 취소된 룸에서는 나가지 않는다`() {
        listOf(RoomStatus.COMPLETED, RoomStatus.CANCELED).forEach { status ->
            givenRoom(status)
            val participation = givenParticipant()

            manager.leaveOnWithdrawal(roomId, memberId, now)

            assertThat(participation.status).describedAs("%s", status).isEqualTo(ParticipationStatus.JOINED)
        }
    }

    private fun assertNothingRecorded() {
        verify(exactly = 0) { roomStatusLogRepository.save(any()) }
        verify(exactly = 0) { outboxEventPublisher.publish(any(), any()) }
    }

    private fun givenRoom(
        status: RoomStatus,
        minCapacity: Short = 2,
        startAt: LocalDateTime = now.plusDays(7),
    ): RoomEntity {
        val room = RoomEntity(
            id = roomId,
            jobPostingId = 1L,
            jobRoleId = 1L,
            resumePublic = false,
            sigunguId = null,
            title = "나가기 규칙 테스트 룸",
            description = null,
            interviewStage = InterviewStage.FIRST,
            interviewType = InterviewType.JOB,
            meetingType = MeetingType.ONLINE,
            minCapacity = minCapacity,
            maxCapacity = 6,
            startAt = startAt,
            durationMinutes = 60,
        )
        when (status) {
            RoomStatus.RECRUITING -> Unit
            RoomStatus.CONFIRMED -> room.confirm()
            RoomStatus.COMPLETED -> {
                room.confirm()
                room.complete()
            }
            RoomStatus.CANCELED -> room.cancel()
            else -> error("메모리에서 만들 수 없는 상태다: $status")
        }
        every { roomRepository.findByIdForUpdate(roomId) } returns room
        every { participationFinder.getJoinedParticipants(roomId) } returns
            listOf(JoinedParticipant(memberId = hostMemberId, isHost = true))
        return room
    }

    private fun givenParticipant(currentParticipants: Int = 3): ParticipationEntity {
        val participation = ParticipationEntity(
            roomId = roomId,
            memberId = memberId,
            participationRole = ParticipationRole.PARTICIPANT,
            status = ParticipationStatus.JOINED,
            joinedAt = now.minusDays(1),
        )
        every {
            participationRepository.findByRoomIdAndMemberIdAndStatusAndDeletedAtIsNull(
                roomId,
                memberId,
                ParticipationStatus.JOINED,
            )
        } returns participation
        every {
            participationRepository.countByRoomIdAndStatusAndDeletedAtIsNull(roomId, ParticipationStatus.JOINED)
        } returns currentParticipants.toLong()
        return participation
    }
}
