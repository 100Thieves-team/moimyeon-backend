package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.QuestionVote
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

// 클로징 제출에 종속된 구성 요소다. 유효 여부와 삭제 수명은 closing_response 가 소유한다.
@Entity
@Table(
    name = "question_vote",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_question_vote_closing_response_question",
            columnNames = ["closing_response_id", "question_id"],
        ),
    ],
)
class QuestionVoteEntity(
    val questionId: Long,
    @Enumerated(EnumType.STRING)
    val vote: QuestionVote,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.MIN

    @UpdateTimestamp
    val updatedAt: LocalDateTime = LocalDateTime.MIN
}
