package io.plady.moimyeon.core.api.controller.v1.request

import io.plady.moimyeon.core.domain.member.Nickname

data class UpdateNicknameRequest(
    val nickname: String,
) {
    fun toNickname(): Nickname = Nickname(nickname)
}
