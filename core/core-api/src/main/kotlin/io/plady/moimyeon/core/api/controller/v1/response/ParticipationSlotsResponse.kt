package io.plady.moimyeon.core.api.controller.v1.response

import io.plady.moimyeon.core.domain.participation.ParticipationSlots

data class ParticipationSlotsResponse(
    val occupied: Long,
    val limit: Int,
    val remaining: Int,
) {
    companion object {
        fun from(slots: ParticipationSlots): ParticipationSlotsResponse = ParticipationSlotsResponse(
            occupied = slots.occupied,
            limit = slots.limit,
            remaining = slots.remaining,
        )
    }
}
