package io.plady.moimyeon.core.qa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.storage.db.core.RoomEntity
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
        return qaTestDataRepository.findRoomsByTitlePrefix(condition.prefix, condition.hostMemberId).map(::toQaRoom)
    }

    @Transactional(readOnly = true)
    fun getRoomIds(condition: QaDataCondition): List<UUID> {
        log.debug { "qa-room.finder.getRoomIds narrowed=${condition.isNarrowed()} hostMemberId=${condition.hostMemberId}" }
        return qaTestDataRepository.findRoomsByTitlePrefix(condition.prefix, condition.hostMemberId).map { it.id }
    }

    private fun toQaRoom(entity: RoomEntity): QaRoom = QaRoom(
        id = entity.id,
        title = entity.title,
        status = entity.status,
        hostMemberId = qaTestDataRepository.findHostMemberId(entity.id),
        createdAt = entity.createdAt,
        applicationCount = qaTestDataRepository.countApplications(entity.id),
        participantCount = qaTestDataRepository.countParticipations(entity.id),
    )
}
