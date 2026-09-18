package io.plady.moimyeon.core.domain.trust

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class ReviewSubmissionManagerTest {
    private val reviewRepository = mockk<ReviewRepository>()
    private val roomRepository = mockk<RoomRepository>()
    private val attendanceRepository = mockk<AttendanceRepository>()
    private val eligibilityValidator = mockk<ReviewEligibilityValidator>()
    private val clock = Clock.fixed(Instant.parse("2026-08-14T03:00:00Z"), ZoneOffset.UTC)
    private val manager = ReviewSubmissionManager(
        reviewRepository,
        roomRepository,
        attendanceRepository,
        eligibilityValidator,
        clock,
    )
    private val roomId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()
    private val command = ReviewSubmissionCommand(
        roomId = roomId,
        authorMemberId = authorMemberId,
        targetMemberId = targetMemberId,
        tags = setOf("시간 약속을 잘 지켜요", "좋은 질문을 해요"),
        content = "실전 같은 질문과 피드백이 좋았어요.",
        anonymous = true,
    )

    @BeforeEach
    fun setUp() {
        every { roomRepository.findByIdForUpdate(roomId) } returns mockk<RoomEntity> {
            every { isActive() } returns true
            every { status } returns RoomStatus.COMPLETED
        }
        every {
            attendanceRepository.findForUpdateByRoomIdAndMemberIdAndDeletedAtIsNull(roomId, authorMemberId)
        } returns mockk<AttendanceEntity> {
            every { status } returns AttendanceStatus.ATTENDED
        }
        every {
            attendanceRepository.findForUpdateByRoomIdAndMemberIdAndDeletedAtIsNull(roomId, targetMemberId)
        } returns mockk<AttendanceEntity> {
            every { status } returns AttendanceStatus.ATTENDED
        }
        every {
            eligibilityValidator.validate(
                RoomStatus.COMPLETED,
                authorMemberId,
                targetMemberId,
                AttendanceStatus.ATTENDED,
                AttendanceStatus.ATTENDED,
            )
        } returns Unit
        every {
            reviewRepository.existsByRoomIdAndAuthorMemberIdAndTargetMemberIdAndDeletedAtIsNull(
                roomId,
                authorMemberId,
                targetMemberId,
            )
        } returns false
    }

    @Test
    fun `룸 행을 잠근 뒤 최신 자격을 확인하고 태그와 텍스트 후기를 제출한다`() {
        val reviewSlot = slot<ReviewEntity>()
        val savedReview = mockk<ReviewEntity> { every { id } returns 1L }
        every { reviewRepository.saveAndFlush(capture(reviewSlot)) } returns savedReview

        val reviewId = manager.submit(command)

        assertThat(reviewId).isEqualTo(1L)
        assertThat(reviewSlot.captured.roomId).isEqualTo(roomId)
        assertThat(reviewSlot.captured.authorMemberId).isEqualTo(authorMemberId)
        assertThat(reviewSlot.captured.targetMemberId).isEqualTo(targetMemberId)
        assertThat(reviewSlot.captured.tags()).containsExactlyInAnyOrderElementsOf(command.tags)
        assertThat(reviewSlot.captured.content).isEqualTo(command.content)
        assertThat(reviewSlot.captured.anonymous).isTrue()
        assertThat(reviewSlot.captured.visibleAt).isEqualTo(LocalDateTime.of(2026, 8, 14, 6, 0))
        verifyOrder {
            roomRepository.findByIdForUpdate(roomId)
            attendanceRepository.findForUpdateByRoomIdAndMemberIdAndDeletedAtIsNull(roomId, authorMemberId)
            attendanceRepository.findForUpdateByRoomIdAndMemberIdAndDeletedAtIsNull(roomId, targetMemberId)
            eligibilityValidator.validate(
                RoomStatus.COMPLETED,
                authorMemberId,
                targetMemberId,
                AttendanceStatus.ATTENDED,
                AttendanceStatus.ATTENDED,
            )
            reviewRepository.existsByRoomIdAndAuthorMemberIdAndTargetMemberIdAndDeletedAtIsNull(
                roomId,
                authorMemberId,
                targetMemberId,
            )
            reviewRepository.saveAndFlush(any())
        }
    }

    @Test
    fun `잠금 뒤 최신 자격 확인이 실패하면 후기를 저장하지 않는다`() {
        every {
            eligibilityValidator.validate(
                RoomStatus.COMPLETED,
                authorMemberId,
                targetMemberId,
                AttendanceStatus.ATTENDED,
                AttendanceStatus.ATTENDED,
            )
        } throws CoreException(CoreErrorType.REVIEW_TARGET_NOT_ATTENDED)

        assertSubmissionFails(CoreErrorType.REVIEW_TARGET_NOT_ATTENDED)

        verify(exactly = 0) {
            reviewRepository.existsByRoomIdAndAuthorMemberIdAndTargetMemberIdAndDeletedAtIsNull(any(), any(), any())
            reviewRepository.saveAndFlush(any())
        }
    }

    @Test
    fun `태그와 텍스트가 모두 없어도 빈 후기를 제출한다`() {
        val reviewSlot = slot<ReviewEntity>()
        val savedReview = mockk<ReviewEntity> { every { id } returns 1L }
        every { reviewRepository.saveAndFlush(capture(reviewSlot)) } returns savedReview

        manager.submit(command.copy(tags = emptySet(), content = ""))

        assertThat(reviewSlot.captured.tags()).isEmpty()
        assertThat(reviewSlot.captured.content).isEmpty()
    }

    @Test
    fun `삭제되지 않은 후기가 있으면 E2005 를 던진다`() {
        every {
            reviewRepository.existsByRoomIdAndAuthorMemberIdAndTargetMemberIdAndDeletedAtIsNull(
                roomId,
                authorMemberId,
                targetMemberId,
            )
        } returns true

        assertSubmissionFails(CoreErrorType.REVIEW_DUPLICATED)

        verify(exactly = 0) { reviewRepository.saveAndFlush(any()) }
    }

    @Test
    fun `동시 제출의 활성 후기 유니크 충돌은 E2005 로 번역한다`() {
        every { reviewRepository.saveAndFlush(any()) } throws DataIntegrityViolationException(
            "후기 중복",
            SQLException("uk_review_room_author_target_active"),
        )

        assertSubmissionFails(CoreErrorType.REVIEW_DUPLICATED)
    }

    @Test
    fun `활성 후기 유니크와 무관한 무결성 위반은 그대로 전파한다`() {
        val unexpected = DataIntegrityViolationException(
            "필수값 누락",
            SQLException("NULL not allowed for column visible_at"),
        )
        every { reviewRepository.saveAndFlush(any()) } throws unexpected

        assertThatThrownBy { manager.submit(command) }.isSameAs(unexpected)
    }

    private fun assertSubmissionFails(errorType: CoreErrorType) {
        assertThatThrownBy { manager.submit(command) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(errorType)
            }
    }
}
