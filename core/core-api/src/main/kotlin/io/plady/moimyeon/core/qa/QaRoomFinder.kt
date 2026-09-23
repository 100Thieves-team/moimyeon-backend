package io.plady.moimyeon.core.qa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.storage.db.core.qa.QaTestDataRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
@Profile(DEV_AUTH_PROFILE_EXPRESSION)
class QaRoomFinder(
    private val qaTestDataRepository: QaTestDataRepository,
) {
    @Transactional(readOnly = true)
    fun getRooms(condition: QaDataCondition): List<QaRoom> {
        log.debug { "qa-room.finder.getRooms narrowed=${condition.isNarrowed()} hostMemberId=${condition.hostMemberId}" }
        val rooms = qaTestDataRepository.findRoomsByTitlePrefix(condition.prefix, condition.hostMemberId)
        val roomIds = rooms.map { it.id }
        val hosts = qaTestDataRepository.findHostMemberIds(roomIds)
        val applications = qaTestDataRepository.countApplicationsByRoomIds(roomIds).associate { it.roomId to it.count }
        val participants = qaTestDataRepository.countParticipationsByRoomIds(roomIds).associate { it.roomId to it.count }
        return rooms.map {
            QaRoom(
                id = it.id,
                title = it.title,
                status = it.status,
                hostMemberId = hosts[it.id],
                createdAt = it.createdAt,
                applicationCount = applications[it.id] ?: 0,
                participantCount = participants[it.id] ?: 0,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getRoomIds(condition: QaDataCondition): List<UUID> {
        log.debug { "qa-room.finder.getRoomIds narrowed=${condition.isNarrowed()} hostMemberId=${condition.hostMemberId}" }
        return qaTestDataRepository.findRoomsByTitlePrefix(condition.prefix, condition.hostMemberId).map { it.id }
    }
}
