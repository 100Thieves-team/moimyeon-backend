package io.plady.moimyeon.core.qa.controller.response

import io.plady.moimyeon.core.qa.QaMember
import java.util.UUID

data class QaMemberResponse(
    val memberId: UUID,
    val nickname: String,
    val email: String,
    val accessToken: String,
) {
    companion object {
        fun from(member: QaMember, accessToken: String): QaMemberResponse = QaMemberResponse(
            memberId = member.id,
            nickname = member.nickname,
            email = member.email,
            accessToken = accessToken, // gate:allow-secret (발급된 토큰을 응답 필드로 전달)
        )
    }
}
