package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.ResumeEntity
import io.plady.moimyeon.storage.db.core.ResumeRepository
import io.plady.moimyeon.storage.db.core.ResumeSubmissionEntity
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import java.util.UUID

// 룸 생성은 쓰기 넷(룸·방장 참여·방장 신청·이력서 제출)이 한 커밋이다.
// 바깥 테스트 트랜잭션을 두지 않는다(testing.md) — 두면 프록시 경계와 커밋 시점이 가려져
// 원자성 단언이 아무것도 보증하지 않게 된다.
@Import(FixedClockTestConfiguration::class)
class RoomManagerIT(
    private val roomManager: RoomManager,
    private val roomRepository: RoomRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val participationRepository: ParticipationRepository,
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
    private val resumeRepository: ResumeRepository,
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
            roomRepository.deleteById(roomId)
        }
        resumeRepository.deleteById(resumeId)
    }

    @Test
    fun `룸을 만들면 룸과 방장 참여와 방장 신청과 이력서 제출이 함께 저장된다`() {
        val roomId = createRoom()

        assertThat(roomRepository.findById(roomId)).isPresent()

        val participation = participationRepository.findAll().single { it.roomId == roomId }
        assertThat(participation.memberId).isEqualTo(hostMemberId)
        assertThat(participation.participationRole).isEqualTo(ParticipationRole.HOST)
        assertThat(participation.status).isEqualTo(ParticipationStatus.JOINED)

        // 방장은 신청을 거치지 않으므로 생성과 동시에 처리된 상태여야 한다.
        val application = roomApplicationRepository.findAll().single { it.roomId == roomId }
        assertThat(application.applicantMemberId).isEqualTo(hostMemberId)
        assertThat(application.status).isEqualTo(RoomApplicationStatus.ACCEPTED)
        assertThat(application.handlerMemberId).isEqualTo(hostMemberId)
        assertThat(application.pendingMemberId).isNull()
        assertThat(application.note).isEmpty()

        val submission = resumeSubmissionRepository.findByRoomApplicationIdAndDeletedAtIsNull(application.id)
        assertThat(submission).isNotNull()
        assertThat(submission!!.roomId).isEqualTo(roomId)
        assertThat(submission.memberId).isEqualTo(hostMemberId)
        assertThat(submission.sourceResumeId).isEqualTo(resumeId)
        assertThat(submission.fileKey).isEqualTo("resumes/$hostMemberId/backend.pdf")
        assertThat(submission.originalName).isEqualTo("backend.pdf")
        assertThat(submission.sizeBytes).isEqualTo(1024L)
        assertThat(submission.contentType).isEqualTo("application/pdf")
    }

    // 넷이 같은 순간이라는 것이 곧 명세다. 자리마다 now() 를 찍으면 여기가 깨진다.
    @Test
    fun `한 번의 룸 생성에서 참여와 신청과 제출 시각이 같다`() {
        val roomId = createRoom()

        val participation = participationRepository.findAll().single { it.roomId == roomId }
        val application = roomApplicationRepository.findAll().single { it.roomId == roomId }
        val submission = resumeSubmissionRepository.findByRoomApplicationIdAndDeletedAtIsNull(application.id)!!

        assertThat(participation.joinedAt).isEqualTo(FIXED_NOW)
        assertThat(application.appliedAt).isEqualTo(FIXED_NOW)
        assertThat(application.handledAt).isEqualTo(FIXED_NOW)
        assertThat(submission.submittedAt).isEqualTo(FIXED_NOW)
    }

    // 제출 INSERT 는 마지막 쓰기라, 여기서 터졌을 때 앞의 셋이 남으면 방장 없는 룸이 만들어진다.
    // original_name 은 VARCHAR(255) 라 넘치면 flush 시점에 실패한다.
    @Test
    fun `이력서 제출 저장이 실패하면 룸도 방장 참여도 방장 신청도 남지 않는다`() {
        val room = newRoom()

        assertThatThrownBy {
            roomManager.create(room, hostMemberId, resumeId, resumeFile(originalName = "가".repeat(300) + ".pdf"))
        }.isInstanceOf(Exception::class.java)

        assertThat(roomRepository.findById(room.id)).isEmpty()
        assertThat(participationRepository.findAll().filter { it.roomId == room.id }).isEmpty()
        assertThat(roomApplicationRepository.findAll().filter { it.roomId == room.id }).isEmpty()
        assertThat(resumeSubmissionRepository.findByRoomIdAndDeletedAtIsNull(room.id)).isEmpty()
    }

    // 제출은 원본을 가리키기만 하는 것이 아니라 그 시점 파일 정보를 복사한다.
    // 복사가 아니면 원본을 지운 뒤 명부에서 제출 파일 정보가 사라진다.
    @Test
    fun `제출 행은 생성 시점의 파일 정보를 복사해 원본 이력서가 바뀌거나 지워져도 유지된다`() {
        persistResume(originalName = "backend.pdf")
        val roomId = createRoom()
        val submission = submissionOf(roomId)

        val resume = resumeRepository.findById(resumeId).orElseThrow()
        resume.rename("이름을 바꾼 이력서")
        resume.delete(FIXED_NOW)
        resumeRepository.saveAndFlush(resume)

        val reloaded = resumeSubmissionRepository.findById(submission.id).orElseThrow()
        assertThat(reloaded.originalName).isEqualTo("backend.pdf")
        assertThat(reloaded.fileKey).isEqualTo("resumes/$hostMemberId/backend.pdf")
        assertThat(reloaded.sourceResumeId).isEqualTo(resumeId)
        assertThat(reloaded.isActive()).isTrue()
    }

    @Test
    fun `같은 이력서로 룸을 두 개 만들면 제출 행이 각각 생긴다`() {
        val firstRoomId = createRoom()
        val secondRoomId = createRoom()

        assertThat(submissionOf(firstRoomId).id).isNotEqualTo(submissionOf(secondRoomId).id)
        assertThat(submissionOf(firstRoomId).sourceResumeId).isEqualTo(resumeId)
        assertThat(submissionOf(secondRoomId).sourceResumeId).isEqualTo(resumeId)
    }

    private fun submissionOf(roomId: UUID): ResumeSubmissionEntity = resumeSubmissionRepository.findByRoomIdAndDeletedAtIsNull(roomId).single()

    private fun createRoom(): UUID {
        val room = newRoom()
        roomManager.create(room, hostMemberId, resumeId, resumeFile())
        return room.id
    }

    private fun newRoom(): Room {
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
        createdRoomIds += room.id
        return room
    }

    private fun persistResume(originalName: String) {
        resumeRepository.saveAndFlush(
            ResumeEntity(
                id = resumeId,
                memberId = hostMemberId,
                name = "백엔드 이력서",
                fileKey = "resumes/$hostMemberId/backend.pdf",
                originalName = originalName,
                sizeBytes = 1024L,
                contentType = "application/pdf",
                summaryStatus = ResumeSummaryStatus.DONE,
                summaryContent = "요약",
                summaryStartedAt = FIXED_NOW,
                isDefault = false,
            ),
        )
    }

    private fun resumeFile(originalName: String = "backend.pdf") = ResumeFile(
        key = "resumes/$hostMemberId/backend.pdf",
        originalName = originalName,
        sizeBytes = 1024L,
        contentType = "application/pdf",
    )
}
