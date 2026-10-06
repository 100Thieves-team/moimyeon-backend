package io.plady.moimyeon.core.domain.room

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class RoomApplicationService(
    private val roomApplicationManager: RoomApplicationManager,
) {
    fun accept(hostMemberId: UUID, roomId: UUID, applicationId: Long): ApplicationDecision {
        log.debug { "room-application.accept hostMemberId=$hostMemberId roomId=$roomId applicationId=$applicationId" }
        return roomApplicationManager.accept(roomId, applicationId, hostMemberId)
    }

    fun reject(hostMemberId: UUID, roomId: UUID, applicationId: Long, reason: RejectReason?): ApplicationDecision {
        log.debug { "room-application.reject hostMemberId=$hostMemberId roomId=$roomId applicationId=$applicationId reason=$reason" }
        return roomApplicationManager.reject(roomId, applicationId, hostMemberId, reason)
    }
}
