package io.plady.moimyeon.core.domain.room

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

// 나가기의 "할 수 있는가" 규칙만 본다. 방장 위임은 협력자가 많아 RoomLeaveIT 가 본다.
class RoomLeaveManagerTest {
    private val now = LocalDateTime.of(2026, 8, 12, 12, 0)

    private val roomRepository = mockk<RoomRepository>()
    private val participationRepository = mockk<ParticipationRepository>(relaxed = true)
    private val roomApplicationRepository = mockk<RoomApplicationRepository>(relaxed = true)
    private val memberFinder = mockk<MemberFinder>(relaxed = true)
    private val roomManager = mockk<RoomManager>(relaxed = true)
    private val manager = RoomLeaveManager(
        roomRepository,
        participationRepository,
        roomApplicationRepository,
        memberFinder,
        roomManager,
        Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
    )

    private val roomId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()

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

    // 확정은 "이 인원으로 진행한다"는 약속이다. 최소까지 내려온 뒤의 이탈은 그 약속을 깬다.
    @Test
    fun `확정된 룸에서 인원이 최소와 같으면 E1423 을 던진다`() {
        givenRoom(RoomStatus.CONFIRMED, minCapacity = 3)
        val participation = givenParticipant(currentParticipants = 3)

        assertThatThrownBy { manager.leave(roomId, memberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_AT_MIN_CAPACITY)
            }
        assertThat(participation.status).isEqualTo(ParticipationStatus.JOINED)
    }

    // COMPLETED 는 메모리에서 만들 수 없다(전이가 없다). 판정을 "나갈 수 있는 상태" 화이트리스트로
    // 쓰면 여기 못 오는 상태도 함께 막힌다 — 그래서 열거가 아니라 화이트리스트여야 한다.
    @Test
    fun `진행 중이거나 취소된 룸에서는 나갈 수 없다`() {
        listOf(RoomStatus.IN_PROGRESS, RoomStatus.CANCELED).forEach { status ->
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

    private fun givenRoom(status: RoomStatus, minCapacity: Short = 2): RoomEntity {
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
            startAt = now.plusDays(7),
            durationMinutes = 60,
        )
        when (status) {
            RoomStatus.RECRUITING -> Unit
            RoomStatus.CONFIRMED -> room.confirm()
            RoomStatus.IN_PROGRESS -> {
                room.confirm()
                room.startProgress(now.plusDays(7))
            }
            RoomStatus.CANCELED -> room.cancel()
            else -> error("메모리에서 만들 수 없는 상태다: $status")
        }
        every { roomRepository.findByIdForUpdate(roomId) } returns room
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
