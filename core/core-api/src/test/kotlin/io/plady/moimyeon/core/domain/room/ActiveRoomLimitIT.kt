package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import java.util.UUID

// 같은 (방장, 공고, 직무) 활성 룸 3개 제한(MOI-330 F1~F3).
// 판정이 쓰기 트랜잭션 안에 있는지도 함께 본다 — 밖에 있으면 거부된 요청의 앞선 쓰기가 남는다.
// 바깥 테스트 트랜잭션을 두지 않는다(testing.md).
@Import(FixedClockTestConfiguration::class)
class ActiveRoomLimitIT(
    private val roomManager: RoomManager,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
) : ContextTest() {
    private val hostMemberId = UUID.randomUUID()
    private val resumeId = UUID.randomUUID()
    private val createdRoomIds = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        createdRoomIds.forEach { roomId ->
            resumeSubmissionRepository.deleteAll(resumeSubmissionRepository.findByRoomIdAndDeletedAtIsNull(roomId))
            roomApplicationRepository.deleteAll(roomApplicationRepository.findAll().filter { it.roomId == roomId })
            participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId == roomId })
            roomStatusLogRepository.deleteAll(roomStatusLogRepository.findAll().filter { it.roomId == roomId })
            if (roomRepository.existsById(roomId)) roomRepository.deleteById(roomId)
        }
    }

    @Test
    fun `같은 공고와 직무의 활성 룸이 3개면 E1427 로 생성을 거부한다`() {
        repeat(3) { createRoom() }

        assertThatThrownBy { createRoom() }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ACTIVE_ROOM_LIMIT_EXCEEDED)
            }
    }

    @Test
    fun `활성 룸이 2개면 생성된다`() {
        repeat(2) { createRoom() }

        val roomId = createRoom()

        assertThat(roomRepository.findById(roomId)).isPresent()
    }

    @Test
    fun `공고가 다르면 활성 룸이 3개여도 생성된다`() {
        repeat(3) { createRoom() }

        val roomId = createRoom(jobPostingId = OTHER_JOB_POSTING_ID)

        assertThat(roomRepository.findById(roomId)).isPresent()
    }

    @Test
    fun `직무가 다르면 활성 룸이 3개여도 생성된다`() {
        repeat(3) { createRoom() }

        val roomId = createRoom(jobRoleId = OTHER_JOB_ROLE_ID)

        assertThat(roomRepository.findById(roomId)).isPresent()
    }

    @Test
    fun `다른 회원의 활성 룸은 내 한도에 세지 않는다`() {
        repeat(3) { createRoom(memberId = UUID.randomUUID()) }

        val roomId = createRoom()

        assertThat(roomRepository.findById(roomId)).isPresent()
    }

    // 판정이 트랜잭션 밖으로 새면 룸만 저장되고 참여가 없는 상태가 남을 수 있다.
    @Test
    fun `한도 초과로 거부되면 룸도 방장 참여도 남지 않는다`() {
        repeat(3) { createRoom() }
        val rejected = newRoom()

        assertThatThrownBy { roomManager.create(rejected, hostMemberId, resumeId, resumeFile()) }
            .isInstanceOf(CoreException::class.java)

        assertThat(roomRepository.findById(rejected.id)).isEmpty()
        assertThat(participationRepository.findAll().filter { it.roomId == rejected.id }).isEmpty()
        assertThat(roomApplicationRepository.findAll().filter { it.roomId == rejected.id }).isEmpty()
    }

    // 취소는 자리를 돌려준다. 활성 집합에서 CANCELED 가 빠져 있다는 것이 여기서 관측된다.
    @Test
    fun `룸을 취소하면 한도가 풀려 다시 생성된다`() {
        val canceled = createRoom()
        repeat(2) { createRoom() }
        roomManager.cancel(canceled, hostMemberId)

        val roomId = createRoom()

        assertThat(roomRepository.findById(roomId)).isPresent()
    }

    private fun createRoom(
        memberId: UUID = hostMemberId,
        jobPostingId: Long = JOB_POSTING_ID,
        jobRoleId: Long = JOB_ROLE_ID,
    ): UUID {
        val room = newRoom(jobPostingId, jobRoleId)
        roomManager.create(room, memberId, resumeId, resumeFile())
        return room.id
    }

    private fun newRoom(
        jobPostingId: Long = JOB_POSTING_ID,
        jobRoleId: Long = JOB_ROLE_ID,
    ): Room {
        val room = Room.create(
            id = UUID.randomUUID(),
            jobPostingId = jobPostingId,
            jobRoleId = jobRoleId,
            title = RoomTitle("백엔드 모의면접 함께 준비해요"),
            description = null,
            interviewStage = InterviewStage.FIRST,
            interviewType = InterviewType.JOB,
            meetingPlace = MeetingPlace.Online,
            capacity = RoomCapacity(min = 2, max = 6),
            schedule = RoomSchedule(startAt = FIXED_NOW.plusDays(7), durationMinutes = 60),
            resumeSharingPolicy = ResumeSharingPolicy.AI_SUMMARY_ONLY,
            now = FIXED_NOW,
        )
        createdRoomIds += room.id
        return room
    }

    private fun resumeFile() = ResumeFile(
        key = "resumes/$hostMemberId/backend.pdf",
        originalName = "backend.pdf",
        sizeBytes = 1024L,
        contentType = "application/pdf",
    )

    companion object {
        private const val JOB_POSTING_ID = 41L
        private const val OTHER_JOB_POSTING_ID = 42L
        private const val JOB_ROLE_ID = 41L
        private const val OTHER_JOB_ROLE_ID = 42L
    }
}
