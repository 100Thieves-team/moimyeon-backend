package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
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
//
// 생성 거부(E1427) 자체는 여기서 못 본다 — 참여 슬롯 게이트(MOI-446)가 항상 먼저 걸려 생성
// 경로에서 E1427 이 도달 불가다(RoomManager 주석). 축 스코프는 사전 조회(getCreationLimit)로,
// 생성 거부와 순서는 ParticipationSlotCreationIT 가 본다.
@Import(FixedClockTestConfiguration::class)
class ActiveRoomLimitIT(
    private val roomManager: RoomManager,
    private val roomFinder: RoomFinder,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val memberRepository: MemberRepository,
) : ContextTest() {
    private val hostMemberId = UUID.randomUUID()
    private val resumeId = UUID.randomUUID()
    private val createdRoomIds = mutableListOf<UUID>()
    private val createdMemberIds = mutableListOf<UUID>()

    // 일정을 하나씩 밀어 매번 다른 룸이 되게 한다. 같은 자연키면 두 번째부터 첫 룸이 그대로
    // 돌아와(MOI-331 멱등) 한도를 세는 전제 자체가 무너진다. 한도의 키에는 start_at 이 없다.
    private var scheduleSeq = 0L

    @AfterEach
    fun cleanUp() {
        createdRoomIds.forEach { roomId ->
            resumeSubmissionRepository.deleteAll(resumeSubmissionRepository.findByRoomIdAndDeletedAtIsNull(roomId))
            roomApplicationRepository.deleteAll(roomApplicationRepository.findAll().filter { it.roomId == roomId })
            participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId == roomId })
            roomStatusLogRepository.deleteAll(roomStatusLogRepository.findAll().filter { it.roomId == roomId })
            if (roomRepository.existsById(roomId)) roomRepository.deleteById(roomId)
        }
        createdMemberIds.forEach(memberRepository::deleteById)
        createdMemberIds.clear()
    }

    @Test
    fun `활성 룸이 2개면 생성된다`() {
        repeat(2) { createRoom() }

        val roomId = createRoom()

        assertThat(roomRepository.findById(roomId)).isPresent()
    }

    // 한도의 키는 (방장, 공고, 직무)다. 생성으로는 이 축을 관측할 수 없어(위 클래스 주석) 조회로 본다.
    @Test
    fun `공고가 다르면 남은 개수에 세지 않는다`() {
        repeat(3) { createRoom() }

        assertThat(roomFinder.getCreationLimit(hostMemberId, OTHER_JOB_POSTING_ID, JOB_ROLE_ID).remaining).isEqualTo(3)
    }

    @Test
    fun `직무가 다르면 남은 개수에 세지 않는다`() {
        repeat(3) { createRoom() }

        assertThat(roomFinder.getCreationLimit(hostMemberId, JOB_POSTING_ID, OTHER_JOB_ROLE_ID).remaining).isEqualTo(3)
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

    // --- 생성 전 경고 조회(F4) -------------------------------------------------
    //
    // 막는 쪽(create)과 같은 쿼리·같은 술어를 봐야 화면의 경고와 생성 결과가 어긋나지 않는다.

    @Test
    fun `활성 룸이 없으면 남은 개수는 3이다`() {
        val limit = roomFinder.getCreationLimit(hostMemberId, JOB_POSTING_ID, JOB_ROLE_ID)

        assertThat(limit.activeRoomCount).isZero()
        assertThat(limit.limit).isEqualTo(3)
        assertThat(limit.remaining).isEqualTo(3)
    }

    @Test
    fun `활성 룸이 두 개면 남은 개수는 1이다`() {
        repeat(2) { createRoom() }

        val limit = roomFinder.getCreationLimit(hostMemberId, JOB_POSTING_ID, JOB_ROLE_ID)

        assertThat(limit.activeRoomCount).isEqualTo(2)
        assertThat(limit.remaining).isEqualTo(1)
    }

    @Test
    fun `한도를 채우면 남은 개수는 0이다`() {
        repeat(3) { createRoom() }

        assertThat(roomFinder.getCreationLimit(hostMemberId, JOB_POSTING_ID, JOB_ROLE_ID).remaining).isZero()
    }

    // 묻는 쪽이 막는 쪽보다 느슨한 집합을 보면 화면이 "만들 수 있다"고 안내한 뒤 서버가 E1427 로 거부한다.
    @Test
    fun `확정된 룸도 남은 개수를 차지한다`() {
        val roomId = createRoom()
        joinParticipant(roomId)
        roomManager.confirm(roomId, hostMemberId)

        assertThat(roomFinder.getCreationLimit(hostMemberId, JOB_POSTING_ID, JOB_ROLE_ID).remaining).isEqualTo(2)
    }

    // 사전 조회는 참조 검증을 하지 않는다. 404 를 내면 화면이 경고 대신 에러를 띄운다.
    @Test
    fun `존재하지 않는 공고로 물으면 0개로 답한다`() {
        repeat(3) { createRoom() }

        val limit = roomFinder.getCreationLimit(hostMemberId, UNKNOWN_JOB_POSTING_ID, JOB_ROLE_ID)

        assertThat(limit.activeRoomCount).isZero()
        assertThat(limit.remaining).isEqualTo(3)
    }

    // 생성 경로가 방장 회원 행을 잠그며 실재를 본다(MOI-331). 방장이 누구든 행이 있어야 한다.
    private fun createRoom(
        memberId: UUID = hostMemberId,
        jobPostingId: Long = JOB_POSTING_ID,
        jobRoleId: Long = JOB_ROLE_ID,
    ): UUID {
        persistMemberIfAbsent(memberId)
        val room = newRoom(jobPostingId, jobRoleId)
        roomManager.create(room, memberId, resumeId, resumeFile())
        return room.id
    }

    private fun persistMemberIfAbsent(memberId: UUID) {
        if (memberRepository.existsById(memberId)) return
        memberRepository.save(activeMember(memberId, "host-moi330-${createdMemberIds.size}"))
        createdMemberIds += memberId
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
            schedule = RoomSchedule(startAt = FIXED_NOW.plusDays(7).plusHours(scheduleSeq++), durationMinutes = 60),
            resumeSharingPolicy = ResumeSharingPolicy.AI_SUMMARY_ONLY,
            now = FIXED_NOW,
        )
        createdRoomIds += room.id
        return room
    }

    // 확정은 최소 진행 인원(2)을 채워야 통과한다. 방장 혼자면 BELOW_MIN_CAPACITY 다.
    private fun joinParticipant(roomId: UUID) {
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = UUID.randomUUID(),
                participationRole = ParticipationRole.PARTICIPANT,
                status = ParticipationStatus.JOINED,
                joinedAt = FIXED_NOW,
            ),
        )
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
        private const val UNKNOWN_JOB_POSTING_ID = 999_999L
    }
}
