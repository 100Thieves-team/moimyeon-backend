package io.plady.moimyeon.core.domain.question

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class QuestionResumeReferenceService(
    private val accessValidator: QuestionCardSetAccessValidator,
    private val referenceReader: QuestionResumeReferenceReader,
) {
    fun getResumeReference(
        requesterMemberId: UUID,
        roomId: UUID,
        targetMemberId: UUID,
    ): QuestionResumeReference {
        accessValidator.validateViewer(roomId, requesterMemberId)
        accessValidator.validateOtherCardSetTarget(roomId, requesterMemberId, targetMemberId)
        return referenceReader.getByRoomAndTarget(roomId, targetMemberId)
    }
}
