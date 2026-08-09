package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

// 수정은 매니저 호출이 자체 트랜잭션을 가지므로 클래스 레벨 @Transactional 없이 트랜잭션 밖에서 호출한다.
class RoomManagerIT(
    val roomManager: RoomManager,
    val roomRepository: RoomRepository,
    val participationRepository: ParticipationRepository,
) : ContextTest() {
    private val roomId: UUID = UUID.randomUUID()
    private val hostId: UUID = UUID.randomUUID()
    private val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

    // 트랜잭션 롤백이 없으므로 이 테스트가 만든 행을 직접 지운다.
    @AfterEach
    fun cleanUp() {
        participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId == roomId })
        roomRepository.deleteById(roomId)
    }

    // 정원 가드를 넣다가 정상 경로를 막는 것이 이 변경에서 가장 그럴듯한 실수라, 그물을 먼저 친다.
    @Test
    fun `모집 중인 룸의 제목과 일정은 그대로 수정된다`() {
        seedRoom(maxCapacity = 6)
        seedHost()

        roomManager.update(roomId, hostId, updateCommand(title = "다시 정한 백엔드 모의면접 준비 룸"))

        val room = roomRepository.findById(roomId).orElseThrow()
        assertThat(room.title).isEqualTo("다시 정한 백엔드 모의면접 준비 룸")
        assertThat(room.startAt).isEqualTo(now.plusDays(5))
    }

    @Test
    fun `참여자가 5명인 룸의 최대 인원을 3명으로 낮추면 거부된다`() {
        seedRoom(maxCapacity = 6)
        seedHost()
        repeat(4) { seedParticipant(UUID.randomUUID()) }

        assertThatThrownBy { roomManager.update(roomId, hostId, updateCommand(min = 2, max = 3)) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_CAPACITY_BELOW_PARTICIPANTS)
            }

        assertThat(roomRepository.findById(roomId).orElseThrow().maxCapacity).isEqualTo(6)
    }

    // 경계 — 이미 들어와 있는 사람 수만큼은 남겨둘 수 있어야 한다. 여기서 막으면 정원을 정확히 맞춘 방장이 갇힌다.
    @Test
    fun `최대 인원을 현재 참여자 수와 같게 낮추는 것은 허용된다`() {
        seedRoom(maxCapacity = 6)
        seedHost()
        repeat(2) { seedParticipant(UUID.randomUUID()) }

        roomManager.update(roomId, hostId, updateCommand(min = 2, max = 3))

        assertThat(roomRepository.findById(roomId).orElseThrow().maxCapacity).isEqualTo(3)
    }

    // 최소 인원은 확정 조건일 뿐이라 현재 인원보다 커도 된다. 확정이 미뤄질 뿐 지금 상태를 깨지 않는다.
    @Test
    fun `최소 인원은 현재 참여자 수보다 크게 올릴 수 있다`() {
        seedRoom(maxCapacity = 8)
        seedHost()
        seedParticipant(UUID.randomUUID())

        roomManager.update(roomId, hostId, updateCommand(min = 5, max = 8))

        assertThat(roomRepository.findById(roomId).orElseThrow().minCapacity).isEqualTo(5)
    }

    // 나간 사람의 자리는 비워지므로 정원 판정에서 세지 않는다. 수락 쪽 판정과 같아야 한다.
    @Test
    fun `나간 참여자는 정원 판정에서 세지 않는다`() {
        seedRoom(maxCapacity = 6)
        seedHost()
        repeat(4) { seedParticipant(UUID.randomUUID(), status = ParticipationStatus.LEFT) }

        roomManager.update(roomId, hostId, updateCommand(min = 2, max = 2))

        assertThat(roomRepository.findById(roomId).orElseThrow().maxCapacity).isEqualTo(2)
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

    private fun seedRoom(maxCapacity: Int) {
        roomRepository.save(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "테스트 룸",
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = maxCapacity.toShort(),
                startAt = now.plusDays(3),
                durationMinutes = 60,
            ),
        )
    }

    private fun seedHost() {
        participationRepository.save(
            ParticipationEntity(
                roomId = roomId,
                memberId = hostId,
                participationRole = ParticipationRole.HOST,
                status = ParticipationStatus.JOINED,
                joinedAt = now,
            ),
        )
    }

    private fun seedParticipant(memberId: UUID, status: ParticipationStatus = ParticipationStatus.JOINED) {
        participationRepository.save(
            ParticipationEntity(
                roomId = roomId,
                memberId = memberId,
                participationRole = ParticipationRole.PARTICIPANT,
                status = status,
                joinedAt = now,
            ),
        )
    }
}
