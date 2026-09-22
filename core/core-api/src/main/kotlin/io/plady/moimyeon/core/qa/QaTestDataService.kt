package io.plady.moimyeon.core.qa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
@Profile(DEV_AUTH_PROFILE_EXPRESSION)
class QaTestDataService(
    private val qaRoomFinder: QaRoomFinder,
    private val qaRoomEraser: QaRoomEraser,
    private val qaMemberResetter: QaMemberResetter,
    private val qaRoomScheduler: QaRoomScheduler,
    private val qaMemberCreator: QaMemberCreator,
    private val qaResumeSummaryCompleter: QaResumeSummaryCompleter,
) {
    fun getRooms(condition: QaDataCondition): List<QaRoom> = qaRoomFinder.getRooms(condition)

    fun deleteRoom(roomId: UUID): QaDeletedRows {
        val deleted = qaRoomEraser.erase(roomId)
        log.info { "qa-test-data.deleteRoom roomId=$roomId ${deleted.toLogValues()}" }
        return deleted
    }

    fun deleteRooms(condition: QaDataCondition): QaDeletedRows {
        val deleted = qaRoomEraser.eraseAll(condition)
        log.info { "qa-test-data.deleteRooms narrowed=${condition.isNarrowed()} hostMemberId=${condition.hostMemberId} ${deleted.toLogValues()}" }
        return deleted
    }

    fun resetMember(memberId: UUID): QaDeletedRows {
        val deleted = qaMemberResetter.reset(memberId)
        log.info { "qa-test-data.resetMember memberId=$memberId ${deleted.toLogValues()}" }
        return deleted
    }

    fun rescheduleRoom(roomId: UUID, startAt: LocalDateTime): QaRoomSchedule {
        val schedule = qaRoomScheduler.reschedule(roomId, startAt)
        log.info { "qa-test-data.rescheduleRoom roomId=$roomId status=${schedule.status} startAt=$startAt" }
        return schedule
    }

    fun createMember(): QaMember {
        val member = qaMemberCreator.create()
        log.info { "qa-test-data.createMember memberId=${member.id}" }
        return member
    }

    fun completeResumeSummary(resumeId: UUID, summary: String): QaResumeSummary {
        val result = qaResumeSummaryCompleter.complete(resumeId, summary)
        log.info { "qa-test-data.completeResumeSummary resumeId=$resumeId status=${result.status} isDefault=${result.isDefault}" }
        return result
    }
}
