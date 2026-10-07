package io.plady.moimyeon.core.domain.trust

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.event.OutboxEvent
import io.plady.moimyeon.core.event.payload.ReviewPublishedEventPayload
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.ApplicationEventsHolder
import org.springframework.test.context.event.RecordApplicationEvents
import java.time.LocalDateTime
import java.util.UUID

// 기록(조건부 UPDATE)과 발행이 한 커밋인지 보려고 바깥 테스트 트랜잭션을 두지 않는다.
@RecordApplicationEvents
class ReviewPublicationRecorderIT(
    private val reviewPublicationRecorder: ReviewPublicationRecorder,
    private val reviewRepository: ReviewRepository,
    private val roomRepository: RoomRepository,
) : ContextTest() {
    // ApplicationEvents 는 생성자로 주입되지 않는다.
    private val applicationEvents: ApplicationEvents
        get() = ApplicationEventsHolder.getRequiredApplicationEvents()

    private val roomId = UUID.randomUUID()
    private val authorId = UUID.randomUUID()
    private val targetId = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 9, 24, 12, 0)
    private val createdReviewIds = mutableListOf<Long>()

    @BeforeEach
    fun setUp() {
        seedRoom()
    }

    @AfterEach
    fun cleanUp() {
        reviewRepository.deleteAllById(createdReviewIds)
        roomRepository.deleteById(roomId)
    }

    @Test
    fun `공개 시각이 지난 후기의 공개 사실을 발행하고 기록한다`() {
        val reviewId = seedReview(visibleAt = now.minusMinutes(1))

        reviewPublicationRecorder.recordPublishedReviews(now)

        assertThat(publishedFacts()).containsExactly(
            ReviewPublishedEventPayload(
                reviewId = reviewId,
                roomId = roomId,
                roomTitle = "후기 알림 테스트 룸",
                authorMemberId = authorId,
                targetMemberId = targetId,
            ),
        )
        assertThat(reviewRepository.findById(reviewId).orElseThrow().notifiedAt).isEqualTo(now)
    }

    @Test
    fun `공개 전 후기는 발행하지 않는다`() {
        val reviewId = seedReview(visibleAt = now.plusMinutes(1))

        reviewPublicationRecorder.recordPublishedReviews(now)

        assertThat(publishedFacts()).isEmpty()
        assertThat(reviewRepository.findById(reviewId).orElseThrow().notifiedAt).isNull()
    }

    @Test
    fun `삭제된 후기는 발행하지 않는다`() {
        seedReview(visibleAt = now.minusMinutes(1), deleted = true)

        reviewPublicationRecorder.recordPublishedReviews(now)

        assertThat(publishedFacts()).isEmpty()
    }

    @Test
    fun `이미 발행한 후기는 다시 발행하지 않는다`() {
        seedReview(visibleAt = now.minusMinutes(1))

        reviewPublicationRecorder.recordPublishedReviews(now)
        reviewPublicationRecorder.recordPublishedReviews(now.plusMinutes(1))

        assertThat(publishedFacts()).hasSize(1)
    }

    @Test
    fun `운영이 가린 후기는 발행하지 않고 기록만 한다`() {
        val reviewId = seedReview(visibleAt = now.minusMinutes(1), hiddenAt = now.minusMinutes(30))

        reviewPublicationRecorder.recordPublishedReviews(now)

        assertThat(publishedFacts()).isEmpty()
        assertThat(reviewRepository.findById(reviewId).orElseThrow().notifiedAt).isEqualTo(now)
    }

    // 다른 IT 가 남긴 후기도 함께 판정되므로 이 룸의 사실만 본다.
    private fun publishedFacts() = applicationEvents.stream(OutboxEvent::class.java)
        .map { it.payload }
        .toList()
        .filterIsInstance<ReviewPublishedEventPayload>()
        .filter { it.roomId == roomId }

    private fun seedReview(
        visibleAt: LocalDateTime,
        deleted: Boolean = false,
        hiddenAt: LocalDateTime? = null,
    ): Long {
        val review = ReviewEntity(
            roomId = roomId,
            authorMemberId = authorId,
            targetMemberId = targetId,
            content = "함께해서 좋았어요",
            anonymous = false,
            visibleAt = visibleAt,
            hiddenAt = hiddenAt,
        )
        if (deleted) review.delete(visibleAt.minusHours(1))
        val id = reviewRepository.saveAndFlush(review).id
        createdReviewIds += id
        return id
    }

    private fun seedRoom() {
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "후기 알림 테스트 룸",
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 4,
                startAt = now.minusDays(1),
                durationMinutes = 60,
            ),
        )
    }
}
