package io.plady.moimyeon.core.api.controller.v1.mock

import io.plady.moimyeon.core.support.error.ErrorType
import io.plady.moimyeon.core.support.error.requireBusiness

// TODO(MOI-316): 닉네임 모킹 규칙(고정 동작). 실 구현(Nickname VO·생성기·중복 검사) 시 제거한다.
object NicknameMock {
    const val SUGGESTED = "명랑한 알파카 42"

    // FE 가 중복(E1007) 플로우를 재현하기 위한 예약 닉네임
    private val UNAVAILABLE = setOf("집요한 수달 07")

    private val LENGTH_RANGE = 2..20
    private val PATTERN = Regex("^[가-힣a-zA-Z0-9 ]+$")

    fun validateFormat(nickname: String) {
        requireBusiness(nickname.length in LENGTH_RANGE && PATTERN.matches(nickname), ErrorType.INVALID_NICKNAME)
    }

    fun isAvailable(nickname: String): Boolean = nickname !in UNAVAILABLE
}
