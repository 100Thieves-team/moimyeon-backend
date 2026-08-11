package io.plady.moimyeon.core.domain.room

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
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
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class RoomManagerTest {
    private val now = LocalDateTime.of(2026, 1, 1, 0, 0)

    private val roomRepository = mockk<RoomRepository>()
    private val participationRepository = mockk<ParticipationRepository>()
    private val manager = RoomManager(
        roomRepository,
        participationRepository,
        Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
    )
    private val roomId = UUID.randomUUID()
    private val hostId = UUID.randomUUID()

    // 수정 계약을 이 테스트가 들고 있다. 무엇이 바뀌고 무엇이 안 바뀌는지를 한자리에서 단언한다.
    @Test
    fun `모집 중인 룸은 편집 가능한 필드만 수정된다`() {
        val room = givenRecruitingRoom()
        givenHost()
        givenParticipants(3)

        manager.update(roomId, hostId, updateCommand(title = "다시 정한 백엔드 모의면접 준비 룸", min = 3, max = 5))

        assertThat(room.title).isEqualTo("다시 정한 백엔드 모의면접 준비 룸")
        assertThat(room.description).isNull()
        assertThat(room.interviewStage).isEqualTo(InterviewStage.FIRST)
        assertThat(room.interviewType).isEqualTo(InterviewType.JOB)
        assertThat(room.meetingType).isEqualTo(MeetingType.ONLINE)
        assertThat(room.sigunguId).isNull()
        assertThat(room.minCapacity).isEqualTo(3)
        assertThat(room.maxCapacity).isEqualTo(5)
        assertThat(room.startAt).isEqualTo(now.plusDays(5))
        assertThat(room.durationMinutes).isEqualTo(60)

        // 식별 참조와 이력서 원본 공개 여부는 수정 대상이 아니다. 공고를 바꾸면 다른 룸이 된다.
        assertThat(room.jobPostingId).isEqualTo(1L)
        assertThat(room.jobRoleId).isEqualTo(2L)
        assertThat(room.resumePublic).isFalse()
        assertThat(room.status).isEqualTo(RoomStatus.RECRUITING)
    }

    @Test
    fun `최대 인원을 현재 참여 인원보다 작게 낮추면 E1417 을 던진다`() {
        val room = givenRecruitingRoom()
        givenHost()
        givenParticipants(5)

        assertFails(CoreErrorType.ROOM_CAPACITY_BELOW_PARTICIPANTS) { updateCommand(min = 2, max = 3) }
        assertThat(room.maxCapacity).isEqualTo(6)
    }

    // 여기서 막으면 정원을 정확히 맞춘 방장이 갇힌다.
    @Test
    fun `최대 인원을 현재 참여 인원과 같게 낮추는 것은 허용된다`() {
        val room = givenRecruitingRoom()
        givenHost()
        givenParticipants(3)

        manager.update(roomId, hostId, updateCommand(min = 2, max = 3))

        assertThat(room.maxCapacity).isEqualTo(3)
    }

    @Test
    fun `최소 인원은 현재 참여 인원보다 크게 올릴 수 있다`() {
        val room = givenRecruitingRoom()
        givenHost()
        givenParticipants(2)

        manager.update(roomId, hostId, updateCommand(min = 5, max = 8))

        assertThat(room.minCapacity).isEqualTo(5)
    }

    // 수락 쪽 정원 판정과 같은 기준을 써야 한다. 나간 사람의 자리는 비워진 것으로 본다.
    @Test
    fun `정원 판정은 참여 중인 인원만 센다`() {
        givenRecruitingRoom()
        givenHost()
        givenParticipants(1)

        manager.update(roomId, hostId, updateCommand(min = 2, max = 2))

        verify {
            participationRepository.countByRoomIdAndStatusAndDeletedAtIsNull(roomId, ParticipationStatus.JOINED)
        }
    }

    @Test
    fun `확정된 룸을 수정하면 E1418 을 던진다`() {
        givenRoomWithStatus(RoomStatus.CONFIRMED)
        givenHost()

        assertFails(CoreErrorType.ROOM_NOT_EDITABLE) { updateCommand() }
    }

    @Test
    fun `취소된 룸을 수정하면 E1418 을 던진다`() {
        givenRoomWithStatus(RoomStatus.CANCELED)
        givenHost()

        assertFails(CoreErrorType.ROOM_NOT_EDITABLE) { updateCommand() }
    }

    @Test
    fun `끝난 룸을 수정하면 E1418 을 던진다`() {
        givenRoomWithStatus(RoomStatus.COMPLETED)
        givenHost()

        assertFails(CoreErrorType.ROOM_NOT_EDITABLE) { updateCommand() }
    }

    @Test
    fun `방장이 아니면 E1406 을 던진다`() {
        val room = givenRecruitingRoom()
        givenHost(isHost = false)

        assertFails(CoreErrorType.ROOM_FORBIDDEN) { updateCommand() }
        assertThat(room.title).isEqualTo("처음 정한 제목입니다")
    }

    @Test
    fun `내려간 룸을 수정하면 E1405 를 던진다`() {
        givenRecruitingRoom().delete(now)

        assertFails(CoreErrorType.ROOM_NOT_FOUND) { updateCommand() }
    }

    private fun assertFails(errorType: CoreErrorType, command: () -> RoomUpdateCommand) {
        assertThatThrownBy { manager.update(roomId, hostId, command()) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }

    // 수정 전 값을 명령과 전부 다르게 둬야 "무엇이 바뀌었나"가 단언으로 드러난다.
    private fun givenRecruitingRoom(): RoomEntity {
        val room = RoomEntity(
            id = roomId,
            jobPostingId = 1L,
            jobRoleId = 2L,
            resumePublic = false,
            sigunguId = 5L,
            title = "처음 정한 제목입니다",
            description = "처음 적은 설명입니다.",
            interviewStage = InterviewStage.ETC,
            interviewType = null,
            meetingType = MeetingType.OFFLINE,
            minCapacity = 2,
            maxCapacity = 6,
            startAt = now.plusDays(3),
            durationMinutes = 30,
        )
        every { roomRepository.findById(roomId) } returns Optional.of(room)
        return room
    }

    // status 는 protected set 이고 전이 메서드가 아직 없어(MOI-396·398) 실제 엔티티로는 만들 수 없는 상태다.
    private fun givenRoomWithStatus(status: RoomStatus): RoomEntity {
        val room = mockk<RoomEntity>(relaxed = true)
        every { room.isActive() } returns true
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
