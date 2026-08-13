package io.plady.moimyeon.core.domain.question

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class QuestionCardSetService(
    private val accessValidator: QuestionCardSetAccessValidator,
    private val cardSetReader: QuestionCardSetReader,
) {
    fun getCardSetOverview(requesterMemberId: UUID, roomId: UUID): QuestionCardSetOverview {
        accessValidator.validateViewer(roomId, requesterMemberId)
        return QuestionCardSetOverview(
            cardSets = cardSetReader.getAllByRoomExceptTarget(roomId, requesterMemberId),
            myCardSetPreparerCount = cardSetReader.countPreparers(roomId, requesterMemberId),
        )
    }

    fun getCardSet(requesterMemberId: UUID, roomId: UUID, targetMemberId: UUID): QuestionCardSet {
        accessValidator.validateViewer(roomId, requesterMemberId)
        accessValidator.validateOtherCardSetTarget(roomId, requesterMemberId, targetMemberId)
        return cardSetReader.getByRoomAndTarget(roomId, targetMemberId)
    }
}
