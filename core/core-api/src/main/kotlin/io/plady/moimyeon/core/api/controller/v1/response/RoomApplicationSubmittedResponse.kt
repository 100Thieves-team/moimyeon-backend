package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.enums.RoomApplicationStatus

data class RoomApplicationSubmittedResponse(
    val applicationId: Long,
    val status: String,
    val statusLabel: String,
) {
    companion object {
        fun of(applicationId: Long): RoomApplicationSubmittedResponse {
            val status = RoomApplicationStatus.PENDING
            return RoomApplicationSubmittedResponse(
                applicationId = applicationId,
                status = status.name,
                statusLabel = status.toApplicantStatusLabel(),
            )
        }
    }
}
