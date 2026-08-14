package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.round.RoundScreen
import java.util.UUID

data class RoundScreenResponse(
    val role: String,
    val interviewee: QuestionMemberResponse,
    val questions: List<QuestionCardResponse>?,
) {
    companion object {
        fun from(screen: RoundScreen, nicknames: Map<UUID, String>): RoundScreenResponse {
            return when (screen) {
                is RoundScreen.Interviewee -> RoundScreenResponse(
                    role = "INTERVIEWEE",
                    interviewee = QuestionMemberResponse.of(screen.intervieweeMemberId, nicknames),
                    questions = null,
                )
                is RoundScreen.Participant -> RoundScreenResponse(
                    role = "PARTICIPANT",
                    interviewee = QuestionMemberResponse.of(screen.intervieweeMemberId, nicknames),
                    questions = screen.questionCardSet.questions.map { QuestionCardResponse.from(it, nicknames) },
                )
            }
        }
    }
}
