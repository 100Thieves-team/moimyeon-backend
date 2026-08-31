package io.plady.moimyeon.core.domain.roomapplication

// 대기 신청 사용 현황(MOI-500). 참여 슬롯(ParticipationSlots)과 같은 이유로 한도를 함께 내린다 —
// 한도가 개인화될 때(PRD §4.1) 서버와 화면이 갈리지 않는다. 파생값(remaining)은 내리지 않는다.
data class PendingApplicationQuota(
    val occupied: Long,
    val limit: Int,
) {
    companion object {
        fun of(occupied: Long): PendingApplicationQuota = PendingApplicationQuota(
            occupied = occupied,
            limit = RoomApplicationQuota.MAX_PENDING.toInt(),
        )
    }
}
