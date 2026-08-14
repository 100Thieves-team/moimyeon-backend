package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.QuestionCardSetDetailResponse
import io.plady.moimyeon.core.api.controller.v1.response.QuestionCardSetsResponse
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.question.QuestionCardSet
import io.plady.moimyeon.core.domain.question.QuestionCardSetService
import io.plady.moimyeon.core.domain.question.QuestionResumeReferenceService
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class QuestionPreparationFacade(
    private val cardSetService: QuestionCardSetService,
    private val resumeReferenceService: QuestionResumeReferenceService,
    private val memberService: MemberService,
) {
    fun getCardSets(viewerMemberId: UUID, roomId: UUID): QuestionCardSetsResponse {
        val overview = cardSetService.getCardSetOverview(viewerMemberId, roomId)
        val nicknames = memberService.getMembers(overview.cardSets.map(QuestionCardSet::targetMemberId))
            .associate { it.id to it.nickname.value }
        return QuestionCardSetsResponse.from(overview.myCardSetPreparerCount, overview.cardSets, nicknames)
    }

    fun getCardSet(
        viewerMemberId: UUID,
        roomId: UUID,
        targetMemberId: UUID,
    ): QuestionCardSetDetailResponse {
        val cardSet = cardSetService.getCardSet(viewerMemberId, roomId, targetMemberId)
        val resumeReference = resumeReferenceService.getResumeReference(viewerMemberId, roomId, targetMemberId)
        val memberIds = buildList {
            add(targetMemberId)
            cardSet.questions.forEach { question ->
                add(question.authorMemberId)
                addAll(question.followUps.map { it.authorMemberId })
            }
        }.distinct()
        val nicknames = memberService.getMembers(memberIds).associate { it.id to it.nickname.value }
        return QuestionCardSetDetailResponse.from(cardSet, resumeReference, nicknames)
    }
}
