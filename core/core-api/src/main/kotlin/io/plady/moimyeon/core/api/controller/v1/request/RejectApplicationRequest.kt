package io.plady.moimyeon.core.api.controller.v1.request

import jakarta.validation.constraints.Size

// 참가 신청 반려(「룸 참여」 §4.4). 방장은 사유 없이 반려할 수 있고, 제공되는 사유 목록에서 골라 넣을 수도 있다.
data class RejectApplicationRequest(
    @field:Size(max = 200)
    val reason: String? = null,
)
