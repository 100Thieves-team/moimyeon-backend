package io.plady.moimyeon.core.domain.room

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class RoomManagerTest {
    private val roomRepository = mockk<RoomRepository>()
    private val participationRepository = mockk<ParticipationRepository>()
    private val manager = RoomManager(roomRepository, participationRepository)

    private val now = LocalDateTime.of(2026, 1, 1, 0, 0)
    private val roomId = UUID.randomUUID()
    private val hostId = UUID.randomUUID()

    // 가드를 넣다가 정상 경로를 막는 것이 이 변경에서 가장 그럴듯한 실수다.
    @Test
    fun `모집 중인 룸은 편집 가능한 필드가 수정된다`() {
        val room = givenRoom()
        givenHost()
        givenParticipants(3)

        manager.update(roomId, hostId, updateCommand(title = "다시 정한 백엔드 모의면접 준비 룸"))

        verify {
            room.update(
                title = "다시 정한 백엔드 모의면접 준비 룸",
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = any(),
                sigunguId = null,
                minCapacity = 2,
                maxCapacity = 6,
                startAt = now.plusDays(5),
                durationMinutes = 60,
            )
        }
    }

    @Test
    fun `최대 인원을 현재 참여 인원보다 작게 낮추면 E1417 을 던진다`() {
        val room = givenRoom()
        givenHost()
        givenParticipants(5)

        assertFails(CoreErrorType.ROOM_CAPACITY_BELOW_PARTICIPANTS) { updateCommand(min = 2, max = 3) }
        verify(exactly = 0) { room.update(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    // 여기서 막으면 정원을 정확히 맞춘 방장이 갇힌다.
    @Test
    fun `최대 인원을 현재 참여 인원과 같게 낮추는 것은 허용된다`() {
        val room = givenRoom()
        givenHost()
        givenParticipants(3)

        manager.update(roomId, hostId, updateCommand(min = 2, max = 3))

        verify { room.update(any(), any(), any(), any(), any(), any(), any(), 3, any(), any()) }
    }

    @Test
    fun `최소 인원은 현재 참여 인원보다 크게 올릴 수 있다`() {
        val room = givenRoom()
        givenHost()
        givenParticipants(2)

        manager.update(roomId, hostId, updateCommand(min = 5, max = 8))

        verify { room.update(any(), any(), any(), any(), any(), any(), 5, any(), any(), any()) }
    }

    // 수락 쪽 정원 판정과 같은 기준을 써야 한다. 나간 사람의 자리는 비워진 것으로 본다.
    @Test
    fun `정원 판정은 참여 중인 인원만 센다`() {
        givenRoom()
        givenHost()
        givenParticipants(1)

        manager.update(roomId, hostId, updateCommand(min = 2, max = 2))

        verify {
            participationRepository.countByRoomIdAndStatusAndDeletedAtIsNull(roomId, ParticipationStatus.JOINED)
        }
    }

    @Test
    fun `확정된 룸을 수정하면 E1418 을 던진다`() {
        givenRoom(RoomStatus.CONFIRMED)
        givenHost()

        assertFails(CoreErrorType.ROOM_NOT_EDITABLE) { updateCommand() }
    }

    @Test
    fun `취소된 룸을 수정하면 E1418 을 던진다`() {
        givenRoom(RoomStatus.CANCELED)
        givenHost()

        assertFails(CoreErrorType.ROOM_NOT_EDITABLE) { updateCommand() }
    }

    @Test
    fun `끝난 룸을 수정하면 E1418 을 던진다`() {
        givenRoom(RoomStatus.COMPLETED)
        givenHost()

        assertFails(CoreErrorType.ROOM_NOT_EDITABLE) { updateCommand() }
    }

    @Test
    fun `방장이 아니면 E1406 을 던진다`() {
        givenRoom()
        givenHost(isHost = false)

        assertFails(CoreErrorType.ROOM_FORBIDDEN) { updateCommand() }
    }

    @Test
    fun `내려간 룸을 수정하면 E1405 를 던진다`() {
        givenRoom(active = false)

        assertFails(CoreErrorType.ROOM_NOT_FOUND) { updateCommand() }
    }

    private fun assertFails(errorType: CoreErrorType, command: () -> RoomUpdateCommand) {
        assertThatThrownBy { manager.update(roomId, hostId, command()) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }

    // status 는 protected set 이고 상태 전이 메서드가 아직 없어(MOI-396·398) 실제 엔티티로는 만들 수 없는 상태다.
    private fun givenRoom(status: RoomStatus = RoomStatus.RECRUITING, active: Boolean = true): RoomEntity {
        val room = mockk<RoomEntity>(relaxed = true)
        every { room.isActive() } returns active
        every { room.status } returns status
        every { roomRepository.findById(roomId) } returns Optional.of(room)
        return room
    }

    private fun givenHost(isHost: Boolean = true) {
        every {
            participationRepository.existsByRoomIdAndMemberIdAndParticipationRoleAndDeletedAtIsNull(
                roomId,
                hostId,
                ParticipationRole.HOST,
            )
        } returns isHost
    }

    private fun givenParticipants(joined: Int) {
        every {
            participationRepository.countByRoomIdAndStatusAndDeletedAtIsNull(roomId, ParticipationStatus.JOINED)
        } returns joined.toLong()
    }

    private fun updateCommand(
        title: String = "백엔드 모의면접 함께 준비해요",
        min: Int = 2,
        max: Int = 6,
    ) = RoomUpdateCommand(
        title = RoomTitle(title),
        description = null,
        interviewStage = InterviewStage.FIRST,
        interviewType = InterviewType.JOB,
        meetingPlace = MeetingPlace.Online,
        capacity = RoomCapacity(min = min, max = max),
        schedule = RoomSchedule(startAt = now.plusDays(5), durationMinutes = 60),
    )
}
