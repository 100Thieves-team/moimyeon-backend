package io.plady.moimyeon.core.qa

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class QaDataSweeperTest {
    private val qaRoomFinder = mockk<QaRoomFinder>()
    private val qaMemberFinder = mockk<QaMemberFinder>()
    private val qaRoomEraser = mockk<QaRoomEraser>()
    private val qaMemberEraser = mockk<QaMemberEraser>()
    private val sweeper = QaDataSweeper(qaRoomFinder, qaMemberFinder, qaRoomEraser, qaMemberEraser)

    private val condition = QaDataCondition(prefix = "[QA]", hostMemberId = null)
    private val roomId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val otherRoomId = UUID.fromString("00000000-0000-0000-0000-000000000102")
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val qaMember = QaMember(id = memberId, nickname = "qa닉네임", email = "qa-x@qa.moimyeon.test")

    @Test
    fun `룸을 하나씩 지워 합산한다`() {
        every { qaRoomFinder.getRoomIds(condition) } returns listOf(roomId, otherRoomId)
        every { qaRoomEraser.erase(roomId) } returns QaDeletedRows(rooms = 1, participants = 2)
        every { qaRoomEraser.erase(otherRoomId) } returns QaDeletedRows(rooms = 1, participants = 2)

        assertThat(sweeper.sweepRooms(condition)).isEqualTo(QaDeletedRows(rooms = 2, participants = 4))
    }

    @Test
    fun `먼저 지워진 룸(E1405)은 건너뛰고 계속 간다`() {
        every { qaRoomFinder.getRoomIds(condition) } returns listOf(roomId, otherRoomId)
        every { qaRoomEraser.erase(roomId) } throws CoreException(CoreErrorType.ROOM_NOT_FOUND)
        every { qaRoomEraser.erase(otherRoomId) } returns QaDeletedRows(rooms = 1)

        assertThat(sweeper.sweepRooms(condition)).isEqualTo(QaDeletedRows(rooms = 1))
    }

    @Test
    fun `룸 하나가 다른 이유로 실패하면 앞선 룸을 지운 채 예외를 전파한다`() {
        every { qaRoomFinder.getRoomIds(condition) } returns listOf(roomId, otherRoomId)
        every { qaRoomEraser.erase(roomId) } returns QaDeletedRows(rooms = 1)
        every { qaRoomEraser.erase(otherRoomId) } throws CoreException(CoreErrorType.QA_DATA_ONLY)

        assertThatThrownBy { sweeper.sweepRooms(condition) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.QA_DATA_ONLY)
            }
        verify(exactly = 1) { qaRoomEraser.erase(roomId) }
    }

    @Test
    fun `회원은 먼저 지워진 회원(E1006)을 건너뛰고, 다른 실패는 앞선 회원을 지운 채 전파한다`() {
        val gone = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val failing = UUID.fromString("00000000-0000-0000-0000-000000000004")
        every { qaMemberFinder.getQaMembers() } returns listOf(qaMember, qaMember.copy(id = gone), qaMember.copy(id = failing))
        every { qaMemberEraser.erase(memberId) } returns QaDeletedRows(members = 1)
        every { qaMemberEraser.erase(gone) } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)
        every { qaMemberEraser.erase(failing) } throws CoreException(CoreErrorType.QA_DATA_ONLY)

        assertThatThrownBy { sweeper.sweepMembers() }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.QA_DATA_ONLY)
            }
        verify(exactly = 1) { qaMemberEraser.erase(memberId) }
        verify(exactly = 1) { qaMemberEraser.erase(gone) }
    }

    @Test
    fun `회원 전원이 지워지면 합산해 돌려준다`() {
        val other = UUID.fromString("00000000-0000-0000-0000-000000000003")
        every { qaMemberFinder.getQaMembers() } returns listOf(qaMember, qaMember.copy(id = other))
        every { qaMemberEraser.erase(memberId) } returns QaDeletedRows(members = 1, profiles = 1)
        every { qaMemberEraser.erase(other) } returns QaDeletedRows(members = 1, profiles = 1)

        assertThat(sweeper.sweepMembers()).isEqualTo(QaDeletedRows(members = 2, profiles = 2))
    }
}
