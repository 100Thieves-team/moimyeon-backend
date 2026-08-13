package io.plady.moimyeon.core.domain.trust

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import io.plady.moimyeon.storage.db.core.ReviewSkipRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Import(FixedTrustClockTestConfiguration::class)
@Transactional
class ReviewSubmissionManagerIT(
    private val reviewService: ReviewService,
    private val reviewEditor: ReviewEditor,
    private val targetFinder: ReviewTargetFinder,
    private val roomRepository: RoomRepository,
    private val attendanceRepository: AttendanceRepository,
    private val reviewRepository: ReviewRepository,
    private val reviewSkipRepository: ReviewSkipRepository,
    private val entityManager: EntityManager,
) : ContextTest() {
    private val authorMemberId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()
    private val recorderMemberId = UUID.randomUUID()

    @Test
    fun `완료 룸의 최신 출석 기록으로 태그와 텍스트 후기를 한 번 저장한다`() {
        val roomId = persistRoom(RoomStatus.COMPLETED)
        persistAttendance(roomId, authorMemberId, AttendanceStatus.ATTENDED)
        persistAttendance(roomId, targetMemberId, AttendanceStatus.ATTENDED)
        val command = ReviewSubmissionCommand(
            roomId = roomId,
            authorMemberId = authorMemberId,
            targetMemberId = targetMemberId,
            tags = setOf("시간 약속을 잘 지켜요", "좋은 질문을 해요"),
            content = "꼬리질문 덕분에 실전처럼 연습했어요.",
        )

        val reviewId = reviewService.submit(command)

        val review = reviewRepository.findById(reviewId).orElseThrow()
        assertThat(review.roomId).isEqualTo(roomId)
        assertThat(review.authorMemberId).isEqualTo(authorMemberId)
        assertThat(review.targetMemberId).isEqualTo(targetMemberId)
        assertThat(review.tags()).containsExactlyInAnyOrderElementsOf(command.tags)
        assertThat(review.content).isEqualTo(command.content)
        assertThat(review.visibleAt).isEqualTo(TRUST_NOW.plusHours(3))

        assertThatThrownBy { reviewService.submit(command) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.REVIEW_DUPLICATED)
            }
        assertThat(
            reviewRepository.findByRoomIdAndAuthorMemberIdAndDeletedAtIsNull(roomId, authorMemberId),
        ).hasSize(1)
    }

    @Test
    fun `결석에서 출석으로 정정된 작성자는 후기를 제출할 수 있다`() {
        val roomId = persistRoom(RoomStatus.COMPLETED)
        val absent = persistAttendance(roomId, authorMemberId, AttendanceStatus.ABSENT)
        absent.delete(TRUST_NOW.minusMinutes(2))
        attendanceRepository.flush()
        persistAttendance(roomId, authorMemberId, AttendanceStatus.ATTENDED)
        persistAttendance(roomId, targetMemberId, AttendanceStatus.ATTENDED)

        val reviewId = reviewService.submit(
            ReviewSubmissionCommand(
                roomId = roomId,
                authorMemberId = authorMemberId,
                targetMemberId = targetMemberId,
                tags = emptySet(),
                content = null,
            ),
        )

        assertThat(reviewRepository.findById(reviewId)).isPresent
    }

    @Test
    fun `진행되지 않았거나 취소된 룸에는 후기가 저장되지 않는다`() {
        val roomId = persistRoom(RoomStatus.CANCELED)

        assertThatThrownBy {
            reviewService.submit(
                ReviewSubmissionCommand(
                    roomId = roomId,
                    authorMemberId = authorMemberId,
                    targetMemberId = targetMemberId,
                    tags = emptySet(),
                    content = null,
                ),
            )
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.REVIEW_NOT_AVAILABLE)
        }
        assertThat(reviewRepository.findAll()).isEmpty()
    }

    @Test
    fun `공개 기준 시각 전에는 작성자가 태그와 텍스트를 수정한다`() {
        val roomId = persistRoom(RoomStatus.COMPLETED)
        persistAttendance(roomId, authorMemberId, AttendanceStatus.ATTENDED)
        persistAttendance(roomId, targetMemberId, AttendanceStatus.ATTENDED)
        val reviewId = reviewService.submit(
            ReviewSubmissionCommand(
                roomId = roomId,
                authorMemberId = authorMemberId,
                targetMemberId = targetMemberId,
                tags = setOf("시간을 잘 지켜요"),
                content = "수정 전 후기",
            ),
        )

        reviewEditor.update(
            ReviewUpdateCommand(
                reviewId = reviewId,
                authorMemberId = authorMemberId,
                tags = setOf("피드백이 구체적이에요", "좋은 질문을 해요"),
                content = "수정한 후기",
            ),
        )
        reviewRepository.flush()
        entityManager.clear()

        val updatedReview = reviewRepository.findById(reviewId).orElseThrow()
        assertThat(updatedReview.tags()).containsExactlyInAnyOrder("피드백이 구체적이에요", "좋은 질문을 해요")
        assertThat(updatedReview.content).isEqualTo("수정한 후기")
    }

    @Test
    fun `후기를 삭제한 뒤 같은 대상자에게 새 후기를 제출한다`() {
        val roomId = persistRoom(RoomStatus.COMPLETED)
        persistAttendance(roomId, authorMemberId, AttendanceStatus.ATTENDED)
        persistAttendance(roomId, targetMemberId, AttendanceStatus.ATTENDED)
        val command = ReviewSubmissionCommand(
            roomId = roomId,
            authorMemberId = authorMemberId,
            targetMemberId = targetMemberId,
            tags = setOf("시간을 잘 지켜요"),
            content = "첫 번째 후기",
        )
        val deletedReviewId = reviewService.submit(command)

        reviewEditor.delete(authorMemberId, deletedReviewId)
        reviewRepository.flush()
        entityManager.clear()
        val resubmittedReviewId = reviewService.submit(command.copy(content = "다시 제출한 후기"))

        assertThat(resubmittedReviewId).isNotEqualTo(deletedReviewId)
        assertThat(reviewRepository.findById(deletedReviewId).orElseThrow().isDeleted()).isTrue()
        val activeReviews = reviewRepository.findByRoomIdAndAuthorMemberIdAndDeletedAtIsNull(
            roomId,
            authorMemberId,
        )
        assertThat(activeReviews).hasSize(1)
        assertThat(activeReviews.single().id).isEqualTo(resubmittedReviewId)
        assertThat(activeReviews.single().content).isEqualTo("다시 제출한 후기")
        assertThat(reviewRepository.findAll()).hasSize(2)
    }

    @Test
    fun `대상별 건너뛰기는 한 번만 기록되고 재진입하면 작성 가능으로 보인다`() {
        val roomId = persistRoom(RoomStatus.COMPLETED)
        persistAttendance(roomId, authorMemberId, AttendanceStatus.ATTENDED)
        persistAttendance(roomId, targetMemberId, AttendanceStatus.ATTENDED)
        val command = ReviewSkipCommand(roomId, authorMemberId, targetMemberId)

        reviewService.skip(command)
        reviewService.skip(command)

        assertThat(reviewSkipRepository.findAll()).hasSize(1)
        assertThat(targetFinder.getTargets(authorMemberId, roomId)).containsExactly(
            ReviewTarget(targetMemberId, ReviewTargetStatus.WRITABLE),
        )
    }

    @Test
    fun `한 대상자를 건너뛴 뒤에도 그 대상자와 남은 대상자에게 후기를 제출한다`() {
        val remainingTargetMemberId = UUID.randomUUID()
        val roomId = persistRoom(RoomStatus.COMPLETED)
        persistAttendance(roomId, authorMemberId, AttendanceStatus.ATTENDED)
        persistAttendance(roomId, targetMemberId, AttendanceStatus.ATTENDED)
        persistAttendance(roomId, remainingTargetMemberId, AttendanceStatus.ATTENDED)
        reviewService.skip(ReviewSkipCommand(roomId, authorMemberId, targetMemberId))

        val remainingReviewId = reviewService.submit(
            ReviewSubmissionCommand(
                roomId = roomId,
                authorMemberId = authorMemberId,
                targetMemberId = remainingTargetMemberId,
                tags = emptySet(),
                content = null,
            ),
        )
        val skippedTargetReviewId = reviewService.submit(
            ReviewSubmissionCommand(
                roomId = roomId,
                authorMemberId = authorMemberId,
                targetMemberId = targetMemberId,
                tags = emptySet(),
                content = null,
            ),
        )

        assertThat(remainingReviewId).isPositive()
        assertThat(skippedTargetReviewId).isPositive().isNotEqualTo(remainingReviewId)
        assertThat(
            reviewRepository.findByRoomIdAndAuthorMemberIdAndDeletedAtIsNull(roomId, authorMemberId),
        ).hasSize(2)
    }

    @Test
    fun `받은 후기는 공개 시각이 지난 활성 미숨김 후기만 최신 공개 순으로 조회한다`() {
        val newest = persistReceivedReview(
            targetMemberId = targetMemberId,
            visibleAt = TRUST_NOW.minusMinutes(1),
            tags = setOf("피드백이 구체적이에요"),
            content = "가장 최근 공개 후기",
        )
        val older = persistReceivedReview(
            targetMemberId = targetMemberId,
            visibleAt = TRUST_NOW.minusMinutes(2),
            tags = setOf("시간을 잘 지켜요"),
            content = "이전에 공개된 후기",
        )
        persistReceivedReview(
            targetMemberId = targetMemberId,
            visibleAt = TRUST_NOW.plusMinutes(1),
            content = "아직 공개 전 후기",
        )
        persistReceivedReview(
            targetMemberId = targetMemberId,
            visibleAt = TRUST_NOW.minusMinutes(3),
            hiddenAt = TRUST_NOW.minusMinutes(1),
            content = "운영 숨김 후기",
        )
        val deleted = persistReceivedReview(
            targetMemberId = targetMemberId,
            visibleAt = TRUST_NOW.minusMinutes(4),
            content = "작성자 삭제 후기",
        )
        deleted.delete(TRUST_NOW.minusMinutes(1))
        persistReceivedReview(
            targetMemberId = UUID.randomUUID(),
            visibleAt = TRUST_NOW.minusMinutes(5),
            content = "다른 대상의 후기",
        )
        reviewRepository.flush()
        entityManager.clear()

        val result = reviewService.getReceivedReviews(targetMemberId)

        assertThat(result).containsExactly(
            ReceivedReview(newest.id, setOf("피드백이 구체적이에요"), "가장 최근 공개 후기"),
            ReceivedReview(older.id, setOf("시간을 잘 지켜요"), "이전에 공개된 후기"),
        )
    }

    @Test
    fun `작성자 회원이 존재하지 않아도 받은 후기는 유지한다`() {
        val review = persistReceivedReview(
            targetMemberId = targetMemberId,
            visibleAt = TRUST_NOW.minusMinutes(1),
            tags = setOf("좋은 질문을 해요"),
            content = "탈퇴 이후에도 남는 후기",
        )

        assertThat(reviewService.getReceivedReviews(targetMemberId)).containsExactly(
            ReceivedReview(review.id, setOf("좋은 질문을 해요"), "탈퇴 이후에도 남는 후기"),
        )
    }

    private fun persistRoom(status: RoomStatus): UUID {
        val roomId = UUID.randomUUID()
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "후기 제출 테스트 룸",
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 4,
                startAt = TRUST_NOW.minusHours(2),
                durationMinutes = 60,
            ),
        )
        entityManager.createNativeQuery("update room set status = :status where id = :roomId")
            .setParameter("status", status.name)
            .setParameter("roomId", roomId)
            .executeUpdate()
        entityManager.clear()
        return roomId
    }

    private fun persistAttendance(
        roomId: UUID,
        memberId: UUID,
        status: AttendanceStatus,
    ): AttendanceEntity {
        return attendanceRepository.saveAndFlush(
            AttendanceEntity(
                roomId = roomId,
                memberId = memberId,
                status = status,
                recorderMemberId = recorderMemberId,
                recordedAt = TRUST_NOW.minusHours(1),
            ),
        )
    }

    private fun persistReceivedReview(
        targetMemberId: UUID,
        visibleAt: LocalDateTime,
        tags: Set<String> = emptySet(),
        content: String? = null,
        hiddenAt: LocalDateTime? = null,
    ) = reviewRepository.saveAndFlush(
        ReviewEntity(
            roomId = UUID.randomUUID(),
            authorMemberId = UUID.randomUUID(),
            targetMemberId = targetMemberId,
            content = content,
            visibleAt = visibleAt,
            hiddenAt = hiddenAt,
            tags = tags,
        ),
    )
}
