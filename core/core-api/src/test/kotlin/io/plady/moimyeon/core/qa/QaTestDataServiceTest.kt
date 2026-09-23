package io.plady.moimyeon.core.qa

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
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
    private val qaMemberFinder = mockk<QaMemberFinder>()
    private val qaRoomEraser = mockk<QaRoomEraser>()
    private val qaMemberEraser = mockk<QaMemberEraser>()
    private val qaMemberResetter = mockk<QaMemberResetter>()
    private val qaRoomScheduler = mockk<QaRoomScheduler>()
    private val qaMemberCreator = mockk<QaMemberCreator>()
    private val qaResumeSummaryCompleter = mockk<QaResumeSummaryCompleter>()
    private val service = QaTestDataService(
        qaRoomFinder,
        qaMemberFinder,
        qaRoomEraser,
        qaMemberEraser,
        qaMemberResetter,
        qaRoomScheduler,
        qaMemberCreator,
        qaResumeSummaryCompleter,
    )

    private val roomId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val memberId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val condition = QaDataCondition(prefix = "[QA]", hostMemberId = null)

    private val qaMember = QaMember(id = memberId, nickname = "qa닉네임", email = "qa-x@qa.moimyeon.test")

    @Test
    fun `QA 데이터 목록은 룸과 QA 생성 회원을 함께 돌려준다`() {
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
        every { qaMemberFinder.getQaMembers() } returns listOf(qaMember)

        assertThat(service.getQaData(condition)).isEqualTo(QaData(rooms = listOf(room), members = listOf(qaMember)))
    }

    @Test
    fun `룸 삭제는 Eraser 가 지운 건수를 그대로 돌려준다`() {
        val deleted = QaDeletedRows(rooms = 1, participants = 2, applications = 3)
        every { qaRoomEraser.erase(roomId) } returns deleted

        assertThat(service.deleteRoom(roomId)).isEqualTo(deleted)
        verify(exactly = 1) { qaRoomEraser.erase(roomId) }
    }

    private val otherRoomId = UUID.fromString("00000000-0000-0000-0000-000000000102")

    @Test
    fun `일괄 삭제는 룸을 하나씩 지워 합산하고 회원은 건드리지 않는다`() {
        every { qaRoomFinder.getRoomIds(condition) } returns listOf(roomId, otherRoomId)
        every { qaRoomEraser.erase(roomId) } returns QaDeletedRows(rooms = 1, participants = 2)
        every { qaRoomEraser.erase(otherRoomId) } returns QaDeletedRows(rooms = 1, participants = 2)

        assertThat(service.deleteQaData(condition)).isEqualTo(QaDeletedRows(rooms = 2, participants = 4))
        verify(exactly = 0) { qaMemberEraser.erase(any()) }
    }

    @Test
    fun `일괄 삭제 중 먼저 지워진 룸(E1405)은 건너뛰고 계속 간다`() {
        every { qaRoomFinder.getRoomIds(condition) } returns listOf(roomId, otherRoomId)
        every { qaRoomEraser.erase(roomId) } throws CoreException(CoreErrorType.ROOM_NOT_FOUND)
        every { qaRoomEraser.erase(otherRoomId) } returns QaDeletedRows(rooms = 1)

        assertThat(service.deleteQaData(condition)).isEqualTo(QaDeletedRows(rooms = 1))
    }

    @Test
    fun `일괄 삭제 중 룸 하나가 다른 이유로 실패하면 예외를 그대로 전파한다`() {
        every { qaRoomFinder.getRoomIds(condition) } returns listOf(roomId, otherRoomId)
        every { qaRoomEraser.erase(roomId) } returns QaDeletedRows(rooms = 1)
        every { qaRoomEraser.erase(otherRoomId) } throws CoreException(CoreErrorType.QA_DATA_ONLY)

        assertThatThrownBy { service.deleteQaData(condition) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.QA_DATA_ONLY)
            }
        verify(exactly = 1) { qaRoomEraser.erase(roomId) }
    }

    @Test
    fun `includeMembers 면 룸 삭제 뒤 QA 생성 회원까지 지우고 합산한다`() {
        val withMembers = condition.copy(includeMembers = true)
        val other = UUID.fromString("00000000-0000-0000-0000-000000000003")
        every { qaRoomFinder.getRoomIds(withMembers) } returns listOf(roomId)
        every { qaRoomEraser.erase(roomId) } returns QaDeletedRows(rooms = 1)
        every { qaMemberFinder.getQaMembers() } returns listOf(qaMember, qaMember.copy(id = other))
        every { qaMemberEraser.erase(memberId) } returns QaDeletedRows(members = 1, profiles = 1)
        every { qaMemberEraser.erase(other) } returns QaDeletedRows(members = 1, profiles = 1)

        assertThat(service.deleteQaData(withMembers)).isEqualTo(QaDeletedRows(rooms = 1, members = 2, profiles = 2))
    }

    @Test
    fun `회원 일괄 삭제 중 먼저 지워진 회원(E1006)은 건너뛰고, 다른 실패는 앞선 회원을 지운 채 전파한다`() {
        val withMembers = condition.copy(includeMembers = true)
        val gone = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val failing = UUID.fromString("00000000-0000-0000-0000-000000000004")
        every { qaRoomFinder.getRoomIds(withMembers) } returns emptyList()
        every { qaMemberFinder.getQaMembers() } returns listOf(qaMember, qaMember.copy(id = gone), qaMember.copy(id = failing))
        every { qaMemberEraser.erase(memberId) } returns QaDeletedRows(members = 1)
        every { qaMemberEraser.erase(gone) } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)
        every { qaMemberEraser.erase(failing) } throws CoreException(CoreErrorType.QA_DATA_ONLY)

        assertThatThrownBy { service.deleteQaData(withMembers) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.QA_DATA_ONLY)
            }
        verify(exactly = 1) { qaMemberEraser.erase(memberId) }
        verify(exactly = 1) { qaMemberEraser.erase(gone) }
    }

    @Test
    fun `QA 생성 회원 삭제는 Eraser 에 위임한다`() {
        val deleted = QaDeletedRows(members = 1, profiles = 1, socialAccounts = 1)
        every { qaMemberEraser.erase(memberId) } returns deleted

        assertThat(service.deleteMember(memberId)).isEqualTo(deleted)
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

    @Test
    fun `룸 일정 변경은 Scheduler 에 위임한다`() {
        val startAt = LocalDateTime.of(2026, 9, 1, 9, 0)
        val schedule = QaRoomSchedule(roomId = roomId, status = RoomStatus.CONFIRMED, startAt = startAt)
        every { qaRoomScheduler.reschedule(roomId, startAt) } returns schedule

        assertThat(service.rescheduleRoom(roomId, startAt)).isEqualTo(schedule)
    }

    @Test
    fun `테스트 회원 생성은 Creator 에 위임한다`() {
        every { qaMemberCreator.create() } returns qaMember

        assertThat(service.createMember()).isEqualTo(qaMember)
    }

    @Test
    fun `이력서 요약 완료는 Completer 에 위임한다`() {
        val resumeId = UUID.fromString("00000000-0000-0000-0000-000000000201")
        val result = QaResumeSummary(resumeId = resumeId, memberId = memberId, status = ResumeSummaryStatus.DONE, content = "요약", isDefault = true)
        every { qaResumeSummaryCompleter.complete(resumeId, "요약") } returns result

        assertThat(service.completeResumeSummary(resumeId, "요약")).isEqualTo(result)
    }
}
