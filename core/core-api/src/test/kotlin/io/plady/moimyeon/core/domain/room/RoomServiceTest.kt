package io.plady.moimyeon.core.domain.room

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.catalog.CatalogRefValidator
import io.plady.moimyeon.core.domain.jobposting.JobPostingFinder
import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.domain.resume.ResumeValidator
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.ResumeSharingPolicy
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

// 소유권·미삭제 규칙 자체는 ResumeValidator 가 갖고 있고 그쪽 테스트가 본다.
// 여기서 보는 것은 흐름 두 가지뿐이다 — 전파되는가, 쓰기보다 먼저 도는가.
class RoomServiceTest {
    private val catalogRefValidator = mockk<CatalogRefValidator>(relaxed = true)
    private val resumeValidator = mockk<ResumeValidator>()
    private val roomManager = mockk<RoomManager>(relaxed = true)
    private val roomFinder = mockk<RoomFinder>()
    private val roomSearchReader = mockk<RoomSearchReader>()
    private val jobPostingFinder = mockk<JobPostingFinder>()
    private val clock = Clock.fixed(Instant.parse("2026-08-11T12:00:00Z"), ZoneOffset.UTC)

    private val service = RoomService(
        catalogRefValidator,
        resumeValidator,
        roomManager,
        roomFinder,
        roomSearchReader,
        jobPostingFinder,
        clock,
    )

    private val hostMemberId = UUID.randomUUID()
    private val resumeId = UUID.randomUUID()

    @Test
    fun `남의 이력서로 룸을 만들면 RESUME_NOT_FOUND 가 전파된다`() {
        every { resumeValidator.validateOwnedBy(hostMemberId, resumeId) } throws
            CoreException(CoreErrorType.RESUME_NOT_FOUND)

        assertThatThrownBy { service.createRoom(hostMemberId, creationCommand()) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.RESUME_NOT_FOUND)
            }
        verify(exactly = 0) { roomManager.create(any(), any(), any(), any()) }
    }

    // 검증이 쓰기 트랜잭션 안으로 들어가면 잠금을 잡은 채 남의 이력서를 조회하게 된다.
    // 카탈로그 참조 검증과 같은 자리(트랜잭션 밖)에 둔다.
    @Test
    fun `이력서 검증은 룸 저장보다 먼저 호출된다`() {
        every { resumeValidator.validateOwnedBy(hostMemberId, resumeId) } returns resumeFile()
        justRun { roomManager.create(any(), any(), any(), any()) }

        service.createRoom(hostMemberId, creationCommand())

        verifyOrder {
            resumeValidator.validateOwnedBy(hostMemberId, resumeId)
            roomManager.create(any(), hostMemberId, resumeId, resumeFile())
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
        schedule = RoomSchedule(
            startAt = LocalDateTime.now(clock).plusDays(7),
            durationMinutes = 60,
        ),
        resumeSharingPolicy = ResumeSharingPolicy.AI_SUMMARY_ONLY,
        resumeId = resumeId,
    )

    private fun resumeFile() = ResumeFile(
        key = "resumes/$hostMemberId/backend.pdf",
        originalName = "backend.pdf",
        sizeBytes = 1024L,
        contentType = "application/pdf",
    )
}
