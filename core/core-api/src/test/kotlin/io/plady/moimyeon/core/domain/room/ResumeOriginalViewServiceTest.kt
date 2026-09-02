package io.plady.moimyeon.core.domain.room

import io.mockk.Called
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.participation.ParticipationValidator
import io.plady.moimyeon.core.domain.resume.ResumeFile
import io.plady.moimyeon.core.domain.resume.ResumeFileStore
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class ResumeOriginalViewServiceTest {
    private val participationValidator = mockk<ParticipationValidator>()
    private val resumeOriginalViewFinder = mockk<ResumeOriginalViewFinder>()
    private val resumeFileStore = mockk<ResumeFileStore>()

    private val now = LocalDateTime.of(2026, 8, 13, 21, 0)
    private val clock = Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())

    private val service = ResumeOriginalViewService(
        participationValidator,
        resumeOriginalViewFinder,
        resumeFileStore,
        clock,
    )

    private val viewerMemberId = UUID.randomUUID()
    private val roomId = UUID.randomUUID()

    @Test
    fun `게이트를 모두 통과하면 URL 과 5분 뒤 만료 시각을 돌려준다`() {
        val file = ResumeFile("resumes/member/resume.pdf", "resume.pdf", 11, "application/pdf")
        every { participationValidator.validateParticipant(roomId, viewerMemberId) } just Runs
        every { resumeOriginalViewFinder.getViewableFile(roomId, 42L) } returns file
        every { resumeFileStore.issueViewUrl(file, Duration.ofMinutes(5)) } returns "https://s3.example.com/presigned"

        val view = service.issueViewUrl(viewerMemberId, roomId, 42L)

        assertThat(view.url).isEqualTo("https://s3.example.com/presigned")
        assertThat(view.expiresAt).isEqualTo(now.plusMinutes(5))
    }

    @Test
    fun `참여자가 아니면 파일 조회 없이 E1419 가 전파된다`() {
        every {
            participationValidator.validateParticipant(roomId, viewerMemberId)
        } throws CoreException(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)

        assertThatThrownBy { service.issueViewUrl(viewerMemberId, roomId, 42L) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)
            }
        verify { resumeOriginalViewFinder wasNot Called }
        verify { resumeFileStore wasNot Called }
    }

    @Test
    fun `열 수 없는 상태의 E1429 가 그대로 전파된다`() {
        every { participationValidator.validateParticipant(roomId, viewerMemberId) } just Runs
        every {
            resumeOriginalViewFinder.getViewableFile(roomId, 42L)
        } throws CoreException(CoreErrorType.RESUME_ORIGINAL_NOT_VIEWABLE)

        assertThatThrownBy { service.issueViewUrl(viewerMemberId, roomId, 42L) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.RESUME_ORIGINAL_NOT_VIEWABLE)
            }
        verify { resumeFileStore wasNot Called }
    }
}
