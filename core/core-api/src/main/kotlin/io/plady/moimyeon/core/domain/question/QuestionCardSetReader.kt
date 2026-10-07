package io.plady.moimyeon.core.domain.question

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.domain.participation.ParticipationFinder
import io.plady.moimyeon.storage.db.core.QuestionEntity
import io.plady.moimyeon.storage.db.core.QuestionRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class QuestionCardSetReader(
    private val participationFinder: ParticipationFinder,
    private val questionRepository: QuestionRepository,
) {
    @Transactional(readOnly = true)
    fun getAllByRoomExceptTarget(roomId: UUID, excludedTargetMemberId: UUID): List<QuestionCardSet> {
        log.debug { "question-card-set.reader.getAllByRoomExceptTarget roomId=$roomId excludedTargetMemberId=$excludedTargetMemberId" }
        val targetMemberIds = participationFinder.getConfirmedParticipantIds(roomId)
            .filterNot { it == excludedTargetMemberId }
        if (targetMemberIds.isEmpty()) return emptyList()

        val questionsByTarget = questionRepository
            .findByRoomIdAndTargetMemberIdInAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                roomId,
                targetMemberIds,
            )
            .groupBy { it.targetMemberId }

        return targetMemberIds.map { targetMemberId ->
            toCardSet(targetMemberId, questionsByTarget[targetMemberId].orEmpty())
        }
    }

    @Transactional(readOnly = true)
    fun getByRoomAndTarget(roomId: UUID, targetMemberId: UUID): QuestionCardSet {
        log.debug { "question-card-set.reader.getByRoomAndTarget roomId=$roomId targetMemberId=$targetMemberId" }
        val questions = questionRepository
            .findByRoomIdAndTargetMemberIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                roomId,
                targetMemberId,
            )
        return toCardSet(targetMemberId, questions)
    }

    @Transactional(readOnly = true)
    fun countPreparers(roomId: UUID, targetMemberId: UUID): Int {
        log.debug { "question-card-set.reader.countPreparers roomId=$roomId targetMemberId=$targetMemberId" }
        return questionRepository
            .countDistinctAuthorsByRoomIdAndTargetMemberIdAndDeletedAtIsNull(roomId, targetMemberId)
            .toInt()
    }

    private fun toCardSet(targetMemberId: UUID, questions: List<QuestionEntity>): QuestionCardSet {
        val followUpsByParentId = questions
            .filter { it.parentQuestionId != null }
            .groupBy { checkNotNull(it.parentQuestionId) }
        val questionCards = questions
            .filter { it.parentQuestionId == null }
            .map { question ->
                QuestionCard(
                    id = question.id,
                    authorMemberId = question.authorMemberId,
                    content = question.content,
                    source = question.source,
                    asked = question.asked,
                    followUps = followUpsByParentId[question.id]
                        .orEmpty()
                        .map { it.toFollowUp() },
                )
            }
        return QuestionCardSet(targetMemberId, questionCards)
    }

    private fun QuestionEntity.toFollowUp(): FollowUpQuestion {
        return FollowUpQuestion(
            id = id,
            authorMemberId = authorMemberId,
            content = content,
            source = source,
            asked = asked,
        )
    }
}
