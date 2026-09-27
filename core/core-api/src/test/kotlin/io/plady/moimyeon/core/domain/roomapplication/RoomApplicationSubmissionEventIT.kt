package io.plady.moimyeon.core.domain.roomapplication

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.domain.room.FIXED_NOW
import io.plady.moimyeon.core.domain.room.FixedClockTestConfiguration
import io.plady.moimyeon.core.domain.room.MeetingPlace
import io.plady.moimyeon.core.domain.room.Room
import io.plady.moimyeon.core.domain.room.RoomCapacity
import io.plady.moimyeon.core.domain.room.RoomManager
import io.plady.moimyeon.core.domain.room.RoomSchedule
import io.plady.moimyeon.core.domain.room.RoomTitle
import io.plady.moimyeon.core.domain.room.activeMember
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import io.plady.moimyeon.core.event.OutboxEvent
import io.plady.moimyeon.core.event.payload.RoomApplicationSubmittedEventPayload
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.ApplicationEventsHolder
import org.springframework.test.context.event.RecordApplicationEvents
import java.util.UUID

@Import(FixedClockTestConfiguration::class)
@RecordApplicationEvents
class RoomApplicationSubmissionEventIT(
    private val roomManager: RoomManager,
    private val roomApplicationSubmissionManager: RoomApplicationSubmissionManager,
    private val roomRepository: RoomRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val participationRepository: ParticipationRepository,
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
    private val memberRepository: MemberRepository,
) : ContextTest() {
    // ApplicationEvents 는 생성자로 주입되지 않는다.
    private val applicationEvents: ApplicationEvents
        get() = ApplicationEventsHolder.getRequiredApplicationEvents()

    private val hostId = UUID.randomUUID()
    private val applicantId = UUID.randomUUID()
    private val createdRoomIds = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        createdRoomIds.forEach { roomId ->
            resumeSubmissionRepository.deleteAll(resumeSubmissionRepository.findByRoomIdAndDeletedAtIsNull(roomId))
            roomApplicationRepository.deleteAll(roomApplicationRepository.findAll().filter { it.roomId == roomId })
            participationRepository.deleteAll(participationRepository.findAll().filter { it.roomId == roomId })
            roomRepository.deleteById(roomId)
        }
        listOf(hostId, applicantId).forEach(memberRepository::deleteById)
    }

    @Test
    fun `참여 신청이 들어오면 방장을 담아 신청 사실을 발행한다`() {
        val roomId = createRoom()
        persistMember(applicantId, "n499-applicant")

        val applicationId = submit(roomId)

        assertThat(submittedFacts()).containsExactly(
            RoomApplicationSubmittedEventPayload(
                applicationId = applicationId,
                roomId = roomId,
                roomTitle = "백엔드 모의면접 함께 준비해요",
                hostMemberId = hostId,
                applicantMemberId = applicantId,
            ),
        )
    }

    @Test
    fun `참여 신청이 거부되면 신청 사실을 발행하지 않는다`() {
        val roomId = createRoom()
        persistMember(applicantId, "n499-applicant")
        submit(roomId)

        assertThatThrownBy { submit(roomId) }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_APPLICATION_DUPLICATED)
        }

        assertThat(submittedFacts()).hasSize(1)
    }

    private fun submittedFacts() = applicationEvents.stream(OutboxEvent::class.java)
        .map { it.payload }
        .toList()
        .filterIsInstance<RoomApplicationSubmittedEventPayload>()

    private fun submit(roomId: UUID) = roomApplicationSubmissionManager.submit(
        applicantId,
        roomId,
        "참여하고 싶습니다.",
        ResumeSubmission(UUID.randomUUID(), resumeFile()),
    )

    private fun createRoom(): UUID {
        persistMember(hostId, "n499-host")
        val room = Room.create(
            id = UUID.randomUUID(),
            jobPostingId = 1L,
            jobRoleId = 1L,
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
        roomManager.create(room, hostId, UUID.randomUUID(), resumeFile())
        createdRoomIds += room.id
        return room.id
    }

    private fun persistMember(memberId: UUID, tag: String) {
        if (memberRepository.existsById(memberId)) return
        memberRepository.save(activeMember(memberId, tag))
    }

    private fun resumeFile() = ResumeFile(
        key = "resumes/$applicantId/backend.pdf",
        originalName = "backend.pdf",
        sizeBytes = 1024L,
        contentType = "application/pdf",
    )
}
