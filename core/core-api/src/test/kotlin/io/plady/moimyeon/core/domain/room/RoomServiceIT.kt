package io.plady.moimyeon.core.domain.room

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.ResumeEntity
import io.plady.moimyeon.storage.db.core.ResumeRepository
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import java.util.UUID

// AI 요약은 이력서 등록 시점에 따로 만들어지고 실패·재시도가 있다(MOI-377).
// 룸 생성이 요약 완료를 조건으로 걸면 재시도 대기 중인 방장이 룸을 못 만든다 —
// 조건이 "없다"는 사실을 고정한다. 검증 도구를 실제로 태우는 생성 경로 전체로 본다.
@Import(FixedClockTestConfiguration::class)
class RoomServiceIT(
    private val roomService: RoomService,
    private val resumeRepository: ResumeRepository,
    private val roomRepository: RoomRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val participationRepository: ParticipationRepository,
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
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
        createdRoomIds.clear()
        resumeRepository.deleteById(resumeId)
    }

    @Test
    fun `AI 요약이 아직 없는 이력서로도 룸을 만들 수 있다`() {
        listOf(ResumeSummaryStatus.PROCESSING, ResumeSummaryStatus.FAILED).forEach { summaryStatus ->
            persistResume(summaryStatus)

            val room = roomService.createRoom(hostMemberId, creationCommand())
            createdRoomIds += room.id

            val submission = resumeSubmissionRepository.findByRoomIdAndDeletedAtIsNull(room.id).single()
            assertThat(submission.sourceResumeId).isEqualTo(resumeId)
            assertThat(submission.memberId).isEqualTo(hostMemberId)
        }
    }

    private fun creationCommand() = RoomCreationCommand(
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
        resumeId = resumeId,
    )

    private fun persistResume(summaryStatus: ResumeSummaryStatus) {
        resumeRepository.findById(resumeId).ifPresent(resumeRepository::delete)
        resumeRepository.saveAndFlush(
            ResumeEntity(
                id = resumeId,
                memberId = hostMemberId,
                name = "백엔드 이력서",
                fileKey = "resumes/$hostMemberId/backend.pdf",
                originalName = "backend.pdf",
                sizeBytes = 1024L,
                contentType = "application/pdf",
                summaryStatus = summaryStatus,
                summaryContent = null,
                summaryStartedAt = FIXED_NOW,
                isDefault = false,
            ),
        )
    }
}
