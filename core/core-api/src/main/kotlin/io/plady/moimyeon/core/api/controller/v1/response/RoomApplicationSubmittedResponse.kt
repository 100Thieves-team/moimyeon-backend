package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.enums.RoomApplicationStatus

data class RoomApplicationSubmittedResponse(
    val applicationId: Long,
    val status: String,
    val statusLabel: String,
) {
    companion object {
        fun of(applicationId: Long): RoomApplicationSubmittedResponse {
            return RoomApplicationSubmittedResponse(
                applicationId = applicationId,
                status = RoomApplicationStatus.PENDING.name,
                statusLabel = "대기 중",
            )
        }
    }
}
