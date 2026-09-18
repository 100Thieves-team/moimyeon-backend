package io.plady.moimyeon.core.domain.roomcomment

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.participation.ParticipationValidator
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class RoomCommentServiceTest {
    private lateinit var participationValidator: ParticipationValidator
    private lateinit var windowReader: RoomCommentWindowReader
    private lateinit var commentReader: RoomCommentReader
    private lateinit var commentManager: RoomCommentManager
    private lateinit var service: RoomCommentService

    private val clock = Clock.fixed(Instant.parse("2026-08-14T10:00:00Z"), ZoneOffset.UTC)
    private val now = LocalDateTime.now(clock)
    private val roomId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        participationValidator = mockk()
        windowReader = mockk()
        commentReader = mockk()
        commentManager = mockk()
        service = RoomCommentService(participationValidator, windowReader, commentReader, commentManager, clock)
    }

    @Test
    fun `참여자가 아니면 E1419 가 전파되고 목록을 읽지 않는다`() {
        every { participationValidator.validateParticipant(roomId, memberId) } throws
            CoreException(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)

        assertThatThrownBy { service.getComments(memberId, roomId, cursor = null, size = 20) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)
            }
        verify(exactly = 0) { commentReader.getPage(any(), any(), any()) }
    }

    @Test
    fun `참여자는 목록과 작성 가능 여부를 함께 받는다`() {
        justRun { participationValidator.validateParticipant(roomId, memberId) }
        val window = RoomCommentWindow(writable = true, readOnlyAt = null)
        val page = RoomCommentPage(comments = emptyList(), nextCursor = null)
        every { windowReader.getWindow(roomId, now) } returns window
        every { commentReader.getPage(roomId, null, 20) } returns page

        val listing = service.getComments(memberId, roomId, cursor = null, size = 20)

        assertThat(listing.window).isEqualTo(window)
        assertThat(listing.page).isEqualTo(page)
    }

    @Test
    fun `작성은 참여 게이트를 지나 쓰기 도구로 위임한다`() {
        justRun { participationValidator.validateParticipant(roomId, memberId) }
        every { commentManager.post(roomId, memberId, "반가워요", now) } returns 41L

        assertThat(service.leaveComment(memberId, roomId, "반가워요")).isEqualTo(41L)
    }

    @Test
    fun `참여자가 아니면 작성도 E1419 로 끊는다`() {
        every { participationValidator.validateParticipant(roomId, memberId) } throws
            CoreException(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)

        assertThatThrownBy { service.leaveComment(memberId, roomId, "반가워요") }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)
            }
        verify(exactly = 0) { commentManager.post(any(), any(), any(), any()) }
    }

    @Test
    fun `삭제는 참여 게이트를 지나 쓰기 도구로 위임한다`() {
        justRun { participationValidator.validateParticipant(roomId, memberId) }
        justRun { commentManager.remove(roomId, memberId, 41L, now) }

        service.deleteComment(memberId, roomId, 41L)

        verify { commentManager.remove(roomId, memberId, 41L, now) }
    }
}
