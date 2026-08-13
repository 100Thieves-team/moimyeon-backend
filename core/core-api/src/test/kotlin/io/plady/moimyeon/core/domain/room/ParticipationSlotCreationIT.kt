package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

// 생성 경로의 참여 슬롯 게이트(MOI-446)와 판정 순서(멱등 → 슬롯 → 활성3, MOI-447 D1-1).
// 슬롯 규칙 자체(어느 상태를 세나, 방장 포함)는 ParticipationFinderIT 가 본다 — 여기는
// 생성이 그 판정을 타는지, 그리고 어느 순서로 타는지만 본다.
class ParticipationSlotCreationIT(
    private val roomManager: RoomManager,
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

    // 자연키에 start_at 이 들어 있어(MOI-331) 시각이 같으면 새 룸이 아니라 기존 룸이 돌아온다.
    // 멱등 테스트만 시각을 고정하고 나머지는 하나씩 민다.
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
        createdRoomIds.clear()
        createdMemberIds.forEach(memberRepository::deleteById)
        createdMemberIds.clear()
    }

    // MOI-446 의 원 시나리오 — 신청 경로(MOI-427)만 막으면 방장으로 3개 + 참여자로 3개 = 6개가 된다.
    @Test
    fun `참여 중인 룸이 3개면 룸 생성이 E1425 로 거부된다`() {
        repeat(3) { occupySlotAsParticipant() }

        assertThatThrownBy { createRoom() }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.PARTICIPATION_SLOT_EXCEEDED)
            }
    }

    // 활성 룸 3개(MOI-330)는 공고·직무별이라 공고를 바꾸면 통과한다. 슬롯 축은 회원이다.
    @Test
    fun `공고가 달라도 방장·참여자 슬롯 합이 3개면 E1425 로 거부된다`() {
        createRoom(jobPostingId = JOB_POSTING_ID)
        repeat(2) { occupySlotAsParticipant() }

        assertThatThrownBy { createRoom(jobPostingId = OTHER_JOB_POSTING_ID) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.PARTICIPATION_SLOT_EXCEEDED)
            }
    }

    // 멱등이 슬롯보다 앞이다(D1-1) — 기존 룸 반환은 새 자원이 아니므로 어떤 한도와도 무관하다(MOI-331 D4).
    @Test
    fun `같은 자연키 재요청은 슬롯이 만석이어도 기존 룸을 반환한다`() {
        val fixedStartAt = FIXED_NOW.plusDays(14)
        val firstRoomId = createRoom(startAt = fixedStartAt)
        repeat(2) { occupySlotAsParticipant() }

        val retried = roomManager.create(newRoom(startAt = fixedStartAt), hostMemberId, resumeId, resumeFile())

        assertThat(retried.roomId).isEqualTo(firstRoomId)
    }

    // 슬롯이 활성3보다 앞이다(D1-1) — 둘 다 초과면 "참여 중인 룸을 정리하라"가 정확한 안내다.
    // 방장 참여도 슬롯을 무는 이상 같은 공고 활성 3개 ⊆ 점유 슬롯 3개라, 이 순서에서 생성 경로의
    // E1427 은 도달 불가가 된다(계획서 §0-1).
    @Test
    fun `슬롯과 활성 룸 한도를 둘 다 넘으면 E1425 를 받는다`() {
        repeat(3) { createRoom() }

        assertThatThrownBy { createRoom() }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.PARTICIPATION_SLOT_EXCEEDED)
            }
    }

    // 남의 룸에 참여자로 들어가 슬롯 하나를 문다. 신청·수락 절차는 이 테스트의 관심이 아니라
    // 참여 행을 직접 만든다(ParticipationFinderIT 와 같은 방식).
    private fun occupySlotAsParticipant() {
        val otherHostId = UUID.randomUUID()
        val room = roomRepository.saveAndFlush(
            RoomEntity(
                id = UUID.randomUUID(),
                jobPostingId = FILLER_JOB_POSTING_ID,
                jobRoleId = JOB_ROLE_ID,
                sigunguId = null,
                title = "슬롯을 무는 남의 룸",
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 6,
                startAt = FIXED_NOW.plusDays(3),
                durationMinutes = 60,
            ),
        )
        createdRoomIds += room.id
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = room.id,
                memberId = otherHostId,
                participationRole = ParticipationRole.HOST,
                status = ParticipationStatus.JOINED,
                joinedAt = FIXED_NOW,
            ),
        )
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = room.id,
                memberId = hostMemberId,
                participationRole = ParticipationRole.PARTICIPANT,
                status = ParticipationStatus.JOINED,
                joinedAt = FIXED_NOW,
            ),
        )
    }

    private fun createRoom(
        jobPostingId: Long = JOB_POSTING_ID,
        startAt: LocalDateTime = FIXED_NOW.plusDays(7).plusHours(scheduleSeq++),
    ): UUID {
        persistMemberIfAbsent(hostMemberId)
        val room = newRoom(jobPostingId, startAt)
        roomManager.create(room, hostMemberId, resumeId, resumeFile())
        return room.id
    }

    private fun persistMemberIfAbsent(memberId: UUID) {
        if (memberRepository.existsById(memberId)) return
        memberRepository.save(activeMember(memberId, "host-moi446-${createdMemberIds.size}"))
        createdMemberIds += memberId
    }

    private fun newRoom(
        jobPostingId: Long = JOB_POSTING_ID,
        startAt: LocalDateTime,
    ): Room {
        val room = Room.create(
            id = UUID.randomUUID(),
            jobPostingId = jobPostingId,
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
        return room
    }

    private fun resumeFile() = ResumeFile(
        key = "resumes/$hostMemberId/backend.pdf",
        originalName = "backend.pdf",
        sizeBytes = 1024L,
        contentType = "application/pdf",
    )

    companion object {
        private const val JOB_POSTING_ID = 46L
        private const val OTHER_JOB_POSTING_ID = 47L
        private const val FILLER_JOB_POSTING_ID = 48L
        private const val JOB_ROLE_ID = 46L
    }
}
