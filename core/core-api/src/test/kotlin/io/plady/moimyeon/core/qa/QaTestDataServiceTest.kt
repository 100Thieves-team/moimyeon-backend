package io.plady.moimyeon.core.qa

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class QaTestDataServiceTest {
    private val qaRoomFinder = mockk<QaRoomFinder>()
    private val qaRoomEraser = mockk<QaRoomEraser>()
    private val qaMemberResetter = mockk<QaMemberResetter>()
    private val service = QaTestDataService(qaRoomFinder, qaRoomEraser, qaMemberResetter)

    private val roomId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val condition = QaDataCondition(prefix = "[QA]", hostMemberId = null)

    @Test
    fun `QA 룸 목록은 Finder 에 위임한다`() {
        val room = QaRoom(
            id = roomId,
            title = "[QA] 목록",
            status = RoomStatus.RECRUITING,
            hostMemberId = memberId,
            createdAt = LocalDateTime.of(2026, 9, 22, 10, 0),
            applicationCount = 1,
            participantCount = 1,
        )
        every { qaRoomFinder.getRooms(condition) } returns listOf(room)

        assertThat(service.getRooms(condition)).containsExactly(room)
    }

    @Test
    fun `룸 삭제는 Eraser 가 지운 건수를 그대로 돌려준다`() {
        val deleted = QaDeletedRows(rooms = 1, participants = 2, applications = 3)
        every { qaRoomEraser.erase(roomId) } returns deleted

        assertThat(service.deleteRoom(roomId)).isEqualTo(deleted)
        verify(exactly = 1) { qaRoomEraser.erase(roomId) }
    }

    @Test
    fun `일괄 삭제는 조건을 Eraser 에 넘기고 합계를 돌려준다`() {
        val deleted = QaDeletedRows(rooms = 2, participants = 4)
        every { qaRoomEraser.eraseAll(condition) } returns deleted

        assertThat(service.deleteRooms(condition)).isEqualTo(deleted)
    }

    @Test
    fun `회원 초기화는 Resetter 에 위임한다`() {
        val deleted = QaDeletedRows(rooms = 1, participants = 3, reviews = 2)
        every { qaMemberResetter.reset(memberId) } returns deleted

        assertThat(service.resetMember(memberId)).isEqualTo(deleted)
    }

    @Test
    fun `QA 데이터가 아닌 룸 삭제 거절(E2201)은 그대로 전파한다`() {
        every { qaRoomEraser.erase(roomId) } throws CoreException(CoreErrorType.QA_DATA_ONLY)

        assertThatThrownBy { service.deleteRoom(roomId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.QA_DATA_ONLY)
            }
    }

    @Test
    fun `없는 회원 초기화 거절(E1006)은 그대로 전파한다`() {
        every { qaMemberResetter.reset(memberId) } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)

        assertThatThrownBy { service.resetMember(memberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND)
            }
    }
}
