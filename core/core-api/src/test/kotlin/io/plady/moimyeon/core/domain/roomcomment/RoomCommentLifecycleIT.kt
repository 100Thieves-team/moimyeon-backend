package io.plady.moimyeon.core.domain.roomcomment

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomGuestbookRepository
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class RoomCommentLifecycleIT(
    private val roomCommentService: RoomCommentService,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val roomGuestbookRepository: RoomGuestbookRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
) : ContextTest() {
    private val hostMemberId = UUID.randomUUID()
    private val participantMemberId = UUID.randomUUID()
    private val leftMemberId = UUID.randomUUID()
    private val outsiderMemberId = UUID.randomUUID()

    // 읽기 전용 파생이 실제 시계(Clock 빈)와 room_status_log 시각의 관계로 판정되므로
    // 이 IT 는 고정 시각 대신 현재 시각 기준 상대 시각으로 데이터를 만든다.
    private val now: LocalDateTime = LocalDateTime.now()

    @Test
    fun `첫 글이 방명록 행을 만들고 목록은 최신순으로 흐른다`() {
        val roomId = seedRecruitingRoom()
        assertThat(roomGuestbookRepository.findByRoomIdAndDeletedAtIsNull(roomId)).isNull()

        val firstId = roomCommentService.leaveComment(hostMemberId, roomId, "다들 반가워요!")
        val secondId = roomCommentService.leaveComment(participantMemberId, roomId, "네 좋습니다.")

        assertThat(roomGuestbookRepository.findByRoomIdAndDeletedAtIsNull(roomId)).isNotNull()

        val listing = roomCommentService.getComments(hostMemberId, roomId, cursor = null, size = 20)
        assertThat(listing.window.writable).isTrue()
        assertThat(listing.window.readOnlyAt).isNull()
        assertThat(listing.page.comments.map { it.id }).containsExactly(secondId, firstId)
        assertThat(listing.page.comments.map { it.authorMemberId })
            .containsExactly(participantMemberId, hostMemberId)
    }

    @Test
    fun `방명록 행이 없는 룸도 목록 조회는 빈 목록으로 동작한다`() {
        val roomId = seedRecruitingRoom()

        val listing = roomCommentService.getComments(participantMemberId, roomId, cursor = null, size = 20)

        assertThat(listing.page.comments).isEmpty()
        assertThat(listing.page.nextCursor).isNull()
        assertThat(listing.window.writable).isTrue()
    }

    @Test
    fun `같은 내용을 연달아 보내면 기존 글을 돌려주고 다른 내용은 새 글이다`() {
        val roomId = seedRecruitingRoom()

        val firstId = roomCommentService.leaveComment(hostMemberId, roomId, "일정 조율해요")
        val retriedId = roomCommentService.leaveComment(hostMemberId, roomId, "일정 조율해요")
        val otherId = roomCommentService.leaveComment(hostMemberId, roomId, "장소는 온라인이에요")

        assertThat(retriedId).isEqualTo(firstId)
        assertThat(otherId).isNotEqualTo(firstId)
        val listing = roomCommentService.getComments(hostMemberId, roomId, cursor = null, size = 20)
        assertThat(listing.page.comments).hasSize(2)
    }

    @Test
    fun `내 글 삭제는 tombstone 으로 남고 재삭제는 조용히 지나간다`() {
        val roomId = seedRecruitingRoom()
        val hostCommentId = roomCommentService.leaveComment(hostMemberId, roomId, "방장 글")
        val myCommentId = roomCommentService.leaveComment(participantMemberId, roomId, "지울 글")

        roomCommentService.deleteComment(participantMemberId, roomId, myCommentId)
        roomCommentService.deleteComment(participantMemberId, roomId, myCommentId)

        val comments = roomCommentService.getComments(hostMemberId, roomId, cursor = null, size = 20).page.comments
        assertThat(comments.map { it.id }).containsExactly(myCommentId, hostCommentId)
        val tombstone = comments.first()
        assertThat(tombstone.isDeleted).isTrue()
        assertThat(tombstone.authorMemberId).isNull()
        assertThat(tombstone.content).isNull()
        assertThat(comments.last().isDeleted).isFalse()
    }

    @Test
    fun `남의 글 삭제는 E2102 없는 글 삭제는 E2103 이다`() {
        val roomId = seedRecruitingRoom()
        val hostCommentId = roomCommentService.leaveComment(hostMemberId, roomId, "방장 글")

        assertThatThrownBy { roomCommentService.deleteComment(participantMemberId, roomId, hostCommentId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_COMMENT_NOT_MINE)
            }
        assertThatThrownBy { roomCommentService.deleteComment(hostMemberId, roomId, 999_999L) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_COMMENT_NOT_FOUND)
            }
    }

    @Test
    fun `제3자와 접근 회수자는 존재 여부조차 알 수 없다`() {
        val roomId = seedRecruitingRoom()
        roomCommentService.leaveComment(hostMemberId, roomId, "참여자만 봐요")

        listOf(outsiderMemberId, leftMemberId).forEach { intruder ->
            assertThatThrownBy { roomCommentService.getComments(intruder, roomId, cursor = null, size = 20) }
                .isInstanceOfSatisfying(CoreException::class.java) {
                    assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)
                }
            assertThatThrownBy { roomCommentService.leaveComment(intruder, roomId, "몰래 등록") }
                .isInstanceOfSatisfying(CoreException::class.java) {
                    assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)
                }
        }
        assertThatThrownBy { roomCommentService.getComments(hostMemberId, UUID.randomUUID(), cursor = null, size = 20) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_PARTICIPANT_FORBIDDEN)
            }
    }

    @Test
    fun `취소 24시간 안은 예고와 함께 작성 가능하고 24시간 뒤는 E2101 이다`() {
        val graceRoomId = seedRecruitingRoom()
        cancelRoom(graceRoomId, occurredAt = now.minusHours(1))
        val gracePeriod = roomCommentService.getComments(hostMemberId, graceRoomId, cursor = null, size = 20)
        assertThat(gracePeriod.window.writable).isTrue()
        assertThat(gracePeriod.window.readOnlyAt).isEqualTo(now.minusHours(1).plusHours(24))
        roomCommentService.leaveComment(hostMemberId, graceRoomId, "유예 중에는 남길 수 있어요")

        val readOnlyRoomId = seedRecruitingRoom()
        val commentId = roomCommentService.leaveComment(hostMemberId, readOnlyRoomId, "전환 전 글")
        cancelRoom(readOnlyRoomId, occurredAt = now.minusHours(25))

        val readOnly = roomCommentService.getComments(hostMemberId, readOnlyRoomId, cursor = null, size = 20)
        assertThat(readOnly.window.writable).isFalse()
        assertThat(readOnly.page.comments).hasSize(1)
        assertThatThrownBy { roomCommentService.leaveComment(hostMemberId, readOnlyRoomId, "늦은 글") }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_COMMENT_READ_ONLY)
            }
        assertThatThrownBy { roomCommentService.deleteComment(hostMemberId, readOnlyRoomId, commentId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_COMMENT_READ_ONLY)
            }
    }

    @Test
    fun `커서로 다음 페이지를 이어 받는다`() {
        val roomId = seedRecruitingRoom()
        val ids = (1..25).map { roomCommentService.leaveComment(hostMemberId, roomId, "글 $it") }

        val firstPage = roomCommentService.getComments(hostMemberId, roomId, cursor = null, size = 20).page
        assertThat(firstPage.comments).hasSize(20)
        val secondPage = roomCommentService
            .getComments(hostMemberId, roomId, cursor = checkNotNull(firstPage.nextCursor), size = 20).page

        assertThat(secondPage.comments).hasSize(5)
        assertThat(secondPage.nextCursor).isNull()
        assertThat(firstPage.comments.map { it.id } + secondPage.comments.map { it.id })
            .containsExactlyElementsOf(ids.reversed())
    }

    private fun seedRecruitingRoom(): UUID {
        val roomId = UUID.randomUUID()
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "방명록 라이프사이클 테스트 룸",
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 3,
                maxCapacity = 4,
                startAt = now.plusDays(7),
                durationMinutes = 60,
            ),
        )
        participationRepository.saveAllAndFlush(
            listOf(
                participation(roomId, hostMemberId, ParticipationRole.HOST, ParticipationStatus.JOINED),
                participation(roomId, participantMemberId, ParticipationRole.PARTICIPANT, ParticipationStatus.JOINED),
                participation(roomId, leftMemberId, ParticipationRole.PARTICIPANT, ParticipationStatus.LEFT),
            ),
        )
        return roomId
    }

    private fun cancelRoom(roomId: UUID, occurredAt: LocalDateTime) {
        val room = roomRepository.findById(roomId).orElseThrow()
        room.cancel()
        roomRepository.saveAndFlush(room)
        roomStatusLogRepository.saveAndFlush(
            RoomStatusLogEntity(
                roomId = roomId,
                transitionType = RoomStatus.CANCELED,
                handlerMemberId = hostMemberId,
                occurredAt = occurredAt,
            ),
        )
    }

    private fun participation(
        roomId: UUID,
        memberId: UUID,
        role: ParticipationRole,
        status: ParticipationStatus,
    ): ParticipationEntity {
        return ParticipationEntity(
            roomId = roomId,
            memberId = memberId,
            participationRole = role,
            status = status,
            joinedAt = now.minusDays(1),
        )
    }
}
