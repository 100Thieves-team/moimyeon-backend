package io.plady.moimyeon.core.domain.roomcomment

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.GuestbookPostEntity
import io.plady.moimyeon.storage.db.core.GuestbookPostRepository
import io.plady.moimyeon.storage.db.core.RoomGuestbookEntity
import io.plady.moimyeon.storage.db.core.RoomGuestbookRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDateTime
import java.util.UUID

class RoomCommentManagerTest {
    private lateinit var roomGuestbookRepository: RoomGuestbookRepository
    private lateinit var guestbookPostRepository: GuestbookPostRepository
    private lateinit var windowReader: RoomCommentWindowReader
    private lateinit var manager: RoomCommentManager

    private val roomId = UUID.randomUUID()
    private val authorId = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 8, 14, 10, 0, 0)
    private val openWindow = RoomCommentWindow(writable = true, readOnlyAt = null)
    private val closedWindow = RoomCommentWindow(writable = false, readOnlyAt = now.minusHours(1))

    @BeforeEach
    fun setUp() {
        roomGuestbookRepository = mockk()
        guestbookPostRepository = mockk()
        windowReader = mockk()
        manager = RoomCommentManager(roomGuestbookRepository, guestbookPostRepository, windowReader)
    }

    @Test
    fun `읽기 전용이면 E2101 을 던지고 아무것도 저장하지 않는다`() {
        every { windowReader.getWindow(roomId, now) } returns closedWindow

        assertThatThrownBy { manager.post(roomId, authorId, "늦은 글", now) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_COMMENT_READ_ONLY)
            }
        verify(exactly = 0) { guestbookPostRepository.save(any()) }
    }

    @Test
    fun `첫 글이면 방명록 행을 만들고 글을 저장한다`() {
        every { windowReader.getWindow(roomId, now) } returns openWindow
        every { roomGuestbookRepository.findForUpdateByRoomIdAndDeletedAtIsNull(roomId) } returns null
        val guestbook = mockk<RoomGuestbookEntity> { every { id } returns 5L }
        every { roomGuestbookRepository.saveAndFlush(any()) } returns guestbook
        every {
            guestbookPostRepository.findFirstByRoomGuestbookIdAndAuthorMemberIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(5L, authorId)
        } returns null
        val saved = slot<GuestbookPostEntity>()
        every { guestbookPostRepository.save(capture(saved)) } answers { mockk { every { id } returns 41L } }

        val commentId = manager.post(roomId, authorId, "다들 반가워요!", now)

        assertThat(commentId).isEqualTo(41L)
        assertThat(saved.captured.roomGuestbookId).isEqualTo(5L)
        assertThat(saved.captured.authorMemberId).isEqualTo(authorId)
        assertThat(saved.captured.content).isEqualTo("다들 반가워요!")
    }

    @Test
    fun `동시 생성으로 유니크가 충돌하면 이미 만들어진 방명록을 다시 잡는다`() {
        every { windowReader.getWindow(roomId, now) } returns openWindow
        val existing = mockk<RoomGuestbookEntity> { every { id } returns 5L }
        every { roomGuestbookRepository.findForUpdateByRoomIdAndDeletedAtIsNull(roomId) } returnsMany listOf(null, existing)
        every { roomGuestbookRepository.saveAndFlush(any()) } throws DataIntegrityViolationException("uk_room_guestbook_room_active")
        every {
            guestbookPostRepository.findFirstByRoomGuestbookIdAndAuthorMemberIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(5L, authorId)
        } returns null
        every { guestbookPostRepository.save(any()) } answers { mockk { every { id } returns 42L } }

        assertThat(manager.post(roomId, authorId, "동시 첫 글", now)).isEqualTo(42L)
    }

    @Test
    fun `동시 요청이 아닌 무결성 위반은 방명록 중복으로 오인하지 않고 전파한다`() {
        every { windowReader.getWindow(roomId, now) } returns openWindow
        every { roomGuestbookRepository.findForUpdateByRoomIdAndDeletedAtIsNull(roomId) } returns null
        val violation = DataIntegrityViolationException("author_member_id cannot be null")
        every { roomGuestbookRepository.saveAndFlush(any()) } throws violation

        assertThatThrownBy { manager.post(roomId, authorId, "글", now) }.isSameAs(violation)
    }

    @Test
    fun `직전 글과 같은 내용을 10초 안에 다시 보내면 저장하지 않고 기존 글 id 를 돌려준다`() {
        every { windowReader.getWindow(roomId, now) } returns openWindow
        val guestbook = mockk<RoomGuestbookEntity> { every { id } returns 5L }
        every { roomGuestbookRepository.findForUpdateByRoomIdAndDeletedAtIsNull(roomId) } returns guestbook
        val last = mockk<GuestbookPostEntity> {
            every { id } returns 41L
            every { content } returns "다들 반가워요!"
            every { createdAt } returns now.minusSeconds(3)
        }
        every {
            guestbookPostRepository.findFirstByRoomGuestbookIdAndAuthorMemberIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(5L, authorId)
        } returns last

        val commentId = manager.post(roomId, authorId, "다들 반가워요!", now)

        assertThat(commentId).isEqualTo(41L)
        verify(exactly = 0) { guestbookPostRepository.save(any()) }
    }

    @Test
    fun `직전 글과 내용이 달라지면 새 글을 저장한다`() {
        every { windowReader.getWindow(roomId, now) } returns openWindow
        val guestbook = mockk<RoomGuestbookEntity> { every { id } returns 5L }
        every { roomGuestbookRepository.findForUpdateByRoomIdAndDeletedAtIsNull(roomId) } returns guestbook
        val last = mockk<GuestbookPostEntity> {
            every { id } returns 41L
            every { content } returns "다들 반가워요!"
            every { createdAt } returns now.minusSeconds(3)
        }
        every {
            guestbookPostRepository.findFirstByRoomGuestbookIdAndAuthorMemberIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(5L, authorId)
        } returns last
        every { guestbookPostRepository.save(any()) } answers { mockk { every { id } returns 42L } }

        assertThat(manager.post(roomId, authorId, "일정 공유드려요", now)).isEqualTo(42L)
    }

    @Test
    fun `읽기 전용이면 삭제도 E2101 로 막는다`() {
        every { windowReader.getWindow(roomId, now) } returns closedWindow

        assertThatThrownBy { manager.remove(roomId, authorId, 41L, now) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_COMMENT_READ_ONLY)
            }
    }

    @Test
    fun `없는 글을 지우면 E2103 을 던진다`() {
        every { windowReader.getWindow(roomId, now) } returns openWindow
        val guestbook = mockk<RoomGuestbookEntity> { every { id } returns 5L }
        every { roomGuestbookRepository.findByRoomIdAndDeletedAtIsNull(roomId) } returns guestbook
        every { guestbookPostRepository.findByIdAndRoomGuestbookId(99L, 5L) } returns null

        assertThatThrownBy { manager.remove(roomId, authorId, 99L, now) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_COMMENT_NOT_FOUND)
            }
    }

    @Test
    fun `방명록 행이 아직 없는 룸에서 지우면 E2103 을 던진다`() {
        every { windowReader.getWindow(roomId, now) } returns openWindow
        every { roomGuestbookRepository.findByRoomIdAndDeletedAtIsNull(roomId) } returns null

        assertThatThrownBy { manager.remove(roomId, authorId, 41L, now) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_COMMENT_NOT_FOUND)
            }
    }

    @Test
    fun `남의 글을 지우면 E2102 를 던진다`() {
        every { windowReader.getWindow(roomId, now) } returns openWindow
        val guestbook = mockk<RoomGuestbookEntity> { every { id } returns 5L }
        every { roomGuestbookRepository.findByRoomIdAndDeletedAtIsNull(roomId) } returns guestbook
        val othersPost = mockk<GuestbookPostEntity> { every { authorMemberId } returns UUID.randomUUID() }
        every { guestbookPostRepository.findByIdAndRoomGuestbookId(41L, 5L) } returns othersPost

        assertThatThrownBy { manager.remove(roomId, authorId, 41L, now) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_COMMENT_NOT_MINE)
            }
    }

    @Test
    fun `이미 삭제한 내 글을 다시 지워도 예외가 나지 않는다`() {
        every { windowReader.getWindow(roomId, now) } returns openWindow
        val guestbook = mockk<RoomGuestbookEntity> { every { id } returns 5L }
        every { roomGuestbookRepository.findByRoomIdAndDeletedAtIsNull(roomId) } returns guestbook
        val deleted = GuestbookPostEntity(roomGuestbookId = 5L, authorMemberId = authorId, content = "지운 글")
        deleted.delete(now.minusMinutes(5))
        every { guestbookPostRepository.findByIdAndRoomGuestbookId(41L, 5L) } returns deleted

        manager.remove(roomId, authorId, 41L, now)

        assertThat(deleted.isDeleted()).isTrue()
    }
}
