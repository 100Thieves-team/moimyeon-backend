package io.plady.moimyeon.core.domain.trust

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TrustService(
    private val trustFinder: TrustFinder,
    private val roomReviewFinder: RoomReviewFinder,
) {
    fun getPublicTrust(memberId: UUID): PublicTrust = trustFinder.getPublicTrust(memberId)

    fun getRoomReviewSummaries(memberId: UUID, roomIds: Collection<UUID>): Map<UUID, RoomReviewSummary> {
        return roomReviewFinder.getSummaries(memberId, roomIds)
    }
}
