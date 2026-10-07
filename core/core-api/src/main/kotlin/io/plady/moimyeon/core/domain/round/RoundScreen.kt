package io.plady.moimyeon.core.domain.round

import io.plady.moimyeon.core.domain.question.QuestionCardSet
import java.util.UUID

sealed interface RoundScreen {
    val intervieweeMemberId: UUID

    data class Interviewee(
        override val intervieweeMemberId: UUID,
    ) : RoundScreen

    data class Participant(
        override val intervieweeMemberId: UUID,
        val questionCardSet: QuestionCardSet,
    ) : RoundScreen
}
