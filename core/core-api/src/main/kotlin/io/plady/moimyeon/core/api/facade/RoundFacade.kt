package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.RoundScreenResponse
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.round.RoundScreen
import io.plady.moimyeon.core.domain.round.RoundService
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RoundFacade(
    private val roundService: RoundService,
    private val memberService: MemberService,
) {
    fun getScreen(memberId: UUID, roomId: UUID, intervieweeMemberId: UUID): RoundScreenResponse {
        val screen = roundService.getScreen(memberId, roomId, intervieweeMemberId)
        val memberIds = buildList {
            add(screen.intervieweeMemberId)
            if (screen is RoundScreen.Participant) {
                screen.questionCardSet.questions.forEach { question ->
                    add(question.authorMemberId)
                    addAll(question.followUps.map { it.authorMemberId })
                }
            }
        }.distinct()
        val nicknames = memberService.getMembers(memberIds).associate { it.id to it.nickname.value }
        return RoundScreenResponse.from(screen, nicknames)
    }
}
