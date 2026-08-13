package io.plady.moimyeon.core.domain.question

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class QuestionCardSetService(
    private val accessValidator: QuestionCardSetAccessValidator,
    private val cardSetReader: QuestionCardSetReader,
) {
    fun getCardSets(requesterMemberId: UUID, roomId: UUID): List<QuestionCardSet> {
        accessValidator.validateViewer(roomId, requesterMemberId)
        return cardSetReader.getAllByRoomExceptTarget(roomId, requesterMemberId)
    }

    fun getMyCardSetPreparerCount(requesterMemberId: UUID, roomId: UUID): Int {
        accessValidator.validateViewer(roomId, requesterMemberId)
        return cardSetReader.countPreparers(roomId, requesterMemberId)
    }

    fun getCardSet(requesterMemberId: UUID, roomId: UUID, targetMemberId: UUID): QuestionCardSet {
        accessValidator.validateViewer(roomId, requesterMemberId)
        accessValidator.validateOtherCardSetTarget(roomId, requesterMemberId, targetMemberId)
        return cardSetReader.getByRoomAndTarget(roomId, targetMemberId)
    }
}
