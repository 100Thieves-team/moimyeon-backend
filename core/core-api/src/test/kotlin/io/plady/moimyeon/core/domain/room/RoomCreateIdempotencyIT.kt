package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import java.time.LocalDateTime
import java.util.UUID

// 룸 생성 멱등(MOI-331 F1·F2). 더블클릭·네트워크 재시도로 같은 룸이 둘 생기지 않아야 한다.
// 자연키는 (방장, 공고, 직무, 시각) 이고 활성 집합은 ActiveRoomLimit 이 소유한다(계획서 D1·D2).
//
// ⚠️ 동시 요청은 여기서 재현하지 않는다(testing.md). 방장 회원 행 락이 빠져도 이 파일은 전부 초록이다 —
//    순차 재호출은 존재 확인만으로 통과하기 때문이다. 락을 지우지 말 것.
// 바깥 테스트 트랜잭션을 두지 않는다(testing.md).
@Import(FixedClockTestConfiguration::class)
class RoomCreateIdempotencyIT(
    private val roomManager: RoomManager,
    private val roomRepository: RoomRepository,
    private val memberRepository: MemberRepository,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
) : ContextTest() {
    private val hostMemberId = UUID.randomUUID()
    private val resumeId = UUID.randomUUID()
    private val createdRoomIds = mutableListOf<UUID>()

    @BeforeEach
    fun persistHostMember() {
        memberRepository.save(activeMember(hostMemberId, "host-moi331"))
    }

    @AfterEach
    fun cleanUp() {
        createdRoomIds.forEach { roomId ->
            resumeSubmissionRepository.deleteAll(resumeSubmissionRepository.findByRoomIdAndDeletedAtIsNull(roomId))
            roomApplicationRepository.deleteAll(roomApplicationRepository.findAll().filter { it.roomId == roomId })
            participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId == roomId })
            roomStatusLogRepository.deleteAll(roomStatusLogRepository.findAll().filter { it.roomId == roomId })
            if (roomRepository.existsById(roomId)) roomRepository.deleteById(roomId)
        }
        memberRepository.deleteById(hostMemberId)
    }

    @Test
    fun `같은 방장이 같은 공고와 직무와 시각으로 두 번 만들면 룸이 하나만 생긴다`() {
        createRoom()
        createRoom()

        assertThat(roomRepository.findByIdInAndDeletedAtIsNull(createdRoomIds)).hasSize(1)
    }

    @Test
    fun `두 번째 요청은 첫 번째 룸의 id 를 그대로 돌려준다`() {
        val first = createRoom()

        val second = createRoom()

        assertThat(second.roomId).isEqualTo(first.roomId)
        assertThat(second.status).isEqualTo(RoomStatus.RECRUITING)
    }

    // 방장의 참여·신청·제출이 두 벌 쌓이면 이력서가 중복 제출된 것으로 남는다.
    // 시도한 룸 id 전부를 대상으로 센다 — 첫 룸만 보면 두 번째가 자기 룸에 자기 행을 만들어도 초록이 된다.
    @Test
    fun `중복 요청은 참여와 신청과 제출 행도 늘리지 않는다`() {
        createRoom()
        createRoom()

        assertThat(participationRepository.findAll().filter { it.roomId in createdRoomIds }).hasSize(1)
        assertThat(roomApplicationRepository.findAll().filter { it.roomId in createdRoomIds }).hasSize(1)
        assertThat(createdRoomIds.flatMap { resumeSubmissionRepository.findByRoomIdAndDeletedAtIsNull(it) }).hasSize(1)
    }

    // 묻는 쪽이 RECRUITING 만 보면 확정된 룸을 못 찾아 같은 조건의 룸이 둘 생긴다.
    // 활성 집합은 ActiveRoomLimit 이 소유한다 — 여기가 그것을 관측하는 자리다.
    @Test
    fun `기존 룸이 확정된 상태여도 그 룸을 돌려준다`() {
        val first = createRoom()
        joinParticipant(first.roomId)
        roomManager.confirm(first.roomId, hostMemberId)

        val second = createRoom()

        assertThat(second.roomId).isEqualTo(first.roomId)
        assertThat(second.status).isEqualTo(RoomStatus.CONFIRMED)
    }

    // 취소한 룸을 같은 조건으로 다시 만드는 것은 허용해야 한다(이슈 코멘트).
    @Test
    fun `취소된 룸과 같은 조건이면 새로 만든다`() {
        val canceled = createRoom()
        roomManager.cancel(canceled.roomId, hostMemberId)

        val recreated = createRoom()

        assertThat(recreated.roomId).isNotEqualTo(canceled.roomId)
        assertThat(roomRepository.findByIdInAndDeletedAtIsNull(createdRoomIds)).hasSize(2)
    }

    // 중복 확인이 3개 게이트보다 뒤면 더블클릭한 방장이 자기 룸 대신 E1427 을 받는다.
    // 아래 둘은 짝이다 — 하나만 두면 게이트를 통째로 지워도 초록이 된다.
    @Test
    fun `활성 룸이 3개인 방장이 기존 룸과 같은 요청을 보내면 그 룸을 돌려준다`() {
        val first = createRoom()
        createRoom(startAt = FIXED_NOW.plusDays(8))
        createRoom(startAt = FIXED_NOW.plusDays(9))

        val again = createRoom()

        assertThat(again.roomId).isEqualTo(first.roomId)
    }

    @Test
    fun `활성 룸이 3개인 방장이 새 룸을 만들려 하면 E1427 로 거부한다`() {
        createRoom()
        createRoom(startAt = FIXED_NOW.plusDays(8))
        createRoom(startAt = FIXED_NOW.plusDays(9))

        assertThatThrownBy { createRoom(startAt = FIXED_NOW.plusDays(10)) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ACTIVE_ROOM_LIMIT_EXCEEDED)
            }
    }

    @Test
    fun `시각이 다르면 다른 룸을 만든다`() {
        val first = createRoom()

        val second = createRoom(startAt = FIXED_NOW.plusDays(8))

        assertThat(second.roomId).isNotEqualTo(first.roomId)
        assertThat(roomRepository.findByIdInAndDeletedAtIsNull(createdRoomIds)).hasSize(2)
    }

    // 요청마다 새 Room 객체를 만든다 — 서버가 id 를 매번 새로 뽑는 실제 경로와 같은 모양이어야
    // "id 가 달라도 같은 룸으로 본다"가 검증된다.
    private fun createRoom(startAt: LocalDateTime = FIXED_NOW.plusDays(7)): RoomCreationResult {
        val room = Room.create(
            id = UUID.randomUUID(),
            jobPostingId = JOB_POSTING_ID,
            jobRoleId = JOB_ROLE_ID,
            title = RoomTitle("백엔드 모의면접 함께 준비해요"),
            description = null,
            interviewStage = InterviewStage.FIRST,
            interviewType = InterviewType.JOB,
            meetingPlace = MeetingPlace.Online,
            capacity = RoomCapacity(min = 2, max = 6),
            schedule = RoomSchedule(startAt = startAt, durationMinutes = 60),
            resumeSharingPolicy = ResumeSharingPolicy.AI_SUMMARY_ONLY,
            now = FIXED_NOW,
        )
        createdRoomIds += room.id
        return roomManager.create(room, hostMemberId, resumeId, resumeFile())
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
        private const val JOB_POSTING_ID = 51L
        private const val JOB_ROLE_ID = 51L
    }
}
