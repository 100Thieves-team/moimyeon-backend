package io.plady.moimyeon.core.domain.trust

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.Session
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class WrittenReviewFinderIT(
    private val finder: WrittenReviewFinder,
    private val reviewRepository: ReviewRepository,
    private val entityManager: EntityManager,
) : ContextTest() {
    private val roomId = UUID.randomUUID()
    private val authorMemberId = UUID.randomUUID()

    @Test
    fun `룸과 작성자가 일치하면 공개 전 활성 후기도 태그까지 조회한다`() {
        val firstTargetMemberId = UUID.randomUUID()
        val secondTargetMemberId = UUID.randomUUID()
        val first = persistReview(
            targetMemberId = firstTargetMemberId,
            tags = setOf("시간을 잘 지켜요", "피드백이 구체적이에요"),
            content = "꼬리질문이 날카로워서 실전 같았어요.",
        )
        val second = persistReview(
            targetMemberId = secondTargetMemberId,
            tags = emptySet(),
            content = null,
        )
        persistReview(roomId = UUID.randomUUID(), targetMemberId = UUID.randomUUID())
        persistReview(authorMemberId = UUID.randomUUID(), targetMemberId = UUID.randomUUID())
        val deleted = persistReview(targetMemberId = UUID.randomUUID())
        deleted.delete(LocalDateTime.of(2026, 9, 3, 12, 0))
        reviewRepository.flush()
        entityManager.clear()

        lateinit var result: List<WrittenReview>
        val queryCount = countQueries {
            result = finder.getWrittenReviews(authorMemberId, roomId)
        }

        assertThat(queryCount).isEqualTo(1)
        assertThat(result).containsExactly(
            WrittenReview(
                id = first.id,
                roomId = roomId,
                targetMemberId = firstTargetMemberId,
                tags = setOf("시간을 잘 지켜요", "피드백이 구체적이에요"),
                content = "꼬리질문이 날카로워서 실전 같았어요.",
                anonymous = true,
            ),
            WrittenReview(
                id = second.id,
                roomId = roomId,
                targetMemberId = secondTargetMemberId,
                tags = emptySet(),
                content = "",
                anonymous = true,
            ),
        )
    }

    private fun countQueries(block: () -> Unit): Long {
        entityManager.flush()
        entityManager.clear()
        val statistics = entityManager.unwrap(Session::class.java).sessionFactory.statistics
        statistics.isStatisticsEnabled = true
        statistics.clear()
        block()
        return statistics.prepareStatementCount
    }

    private fun persistReview(
        roomId: UUID = this.roomId,
        authorMemberId: UUID = this.authorMemberId,
        targetMemberId: UUID,
        tags: Set<String> = emptySet<String>(),
        content: String? = "후기",
    ): ReviewEntity {
        return reviewRepository.saveAndFlush(
            ReviewEntity(
                roomId = roomId,
                authorMemberId = authorMemberId,
                targetMemberId = targetMemberId,
                content = content,
                anonymous = true,
                visibleAt = LocalDateTime.of(2099, 9, 3, 15, 0),
                tags = tags,
            ),
        )
    }
}
