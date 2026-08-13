package io.plady.moimyeon.core.api.controller.v1.response

import java.util.UUID

data class QuestionMemberResponse(
    val memberId: UUID,
    val nickname: String,
) {
    companion object {
        fun of(memberId: UUID, nicknames: Map<UUID, String>): QuestionMemberResponse {
            return QuestionMemberResponse(
                memberId = memberId,
                nickname = nicknames[memberId] ?: WITHDRAWN_QUESTION_MEMBER_NICKNAME,
            )
        }
    }
}

private const val WITHDRAWN_QUESTION_MEMBER_NICKNAME = "탈퇴한 회원"
