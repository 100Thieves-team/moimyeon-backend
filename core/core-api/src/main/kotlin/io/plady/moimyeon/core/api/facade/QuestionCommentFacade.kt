package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.QuestionCommentsResponse
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.question.QuestionCommentCursor
import io.plady.moimyeon.core.domain.question.QuestionCommentService
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class QuestionCommentFacade(
    private val questionCommentService: QuestionCommentService,
    private val memberService: MemberService,
) {
    fun getComments(
        memberId: UUID,
        roomId: UUID,
        intervieweeMemberId: UUID,
        questionId: Long,
        cursor: QuestionCommentCursor?,
    ): QuestionCommentsResponse {
        val page = questionCommentService.getComments(memberId, roomId, intervieweeMemberId, questionId, cursor)
        val nicknames = memberService.getMembers(page.comments.map { it.authorMemberId }.distinct())
            .associate { it.id to it.nickname.value }
        return QuestionCommentsResponse.from(page, memberId, nicknames)
    }
}
