package io.plady.moimyeon.core.domain.participation

// 참여 슬롯 현황(MOI-447). 개수만 내리면 화면이 한도 3 을 스스로 알아야 하고, 그러면 한도가
// 개인화될 때 서버와 화면이 갈린다 — RoomCreationLimit 과 같은 이유로 한도·잔여를 함께 내린다.
data class ParticipationSlots(
    val occupied: Long,
    val limit: Int,
    val remaining: Int,
) {
    companion object {
        fun of(occupied: Long): ParticipationSlots = ParticipationSlots(
            occupied = occupied,
            limit = ParticipationSlot.MAX.toInt(),
            remaining = ParticipationSlot.remaining(occupied),
        )
    }
}
