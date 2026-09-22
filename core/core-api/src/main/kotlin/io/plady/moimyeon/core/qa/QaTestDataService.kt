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
    private val qaMemberFinder: QaMemberFinder,
    private val qaRoomEraser: QaRoomEraser,
    private val qaMemberEraser: QaMemberEraser,
    private val qaMemberResetter: QaMemberResetter,
    private val qaRoomScheduler: QaRoomScheduler,
    private val qaMemberCreator: QaMemberCreator,
    private val qaResumeSummaryCompleter: QaResumeSummaryCompleter,
) {
    fun getQaData(condition: QaDataCondition): QaData = QaData(rooms = qaRoomFinder.getRooms(condition), members = qaMemberFinder.getQaMembers())

    fun deleteRoom(roomId: UUID): QaDeletedRows {
        val deleted = qaRoomEraser.erase(roomId)
        log.info { "qa-test-data.deleteRoom roomId=$roomId ${deleted.toLogValues()}" }
        return deleted
    }

    fun deleteQaData(condition: QaDataCondition): QaDeletedRows {
        val rooms = qaRoomEraser.eraseAll(condition)
        val members = if (condition.includeMembers) eraseQaMembers() else QaDeletedRows.NONE
        val deleted = rooms + members
        log.info {
            "qa-test-data.deleteQaData narrowed=${condition.isNarrowed()} hostMemberId=${condition.hostMemberId} " +
                "includeMembers=${condition.includeMembers} ${deleted.toLogValues()}"
        }
        return deleted
    }

    // 회원 한 명이 한 트랜잭션. 한 명이 실패하면 앞선 회원은 이미 지워진 상태이며 실패 회원을 로그로 남긴다.
    private fun eraseQaMembers(): QaDeletedRows = qaMemberFinder.getQaMembers().fold(QaDeletedRows.NONE) { acc, member ->
        acc + runCatching { qaMemberEraser.erase(member.id) }
            .onFailure { log.warn(it) { "qa-test-data.deleteQaData.memberFailed memberId=${member.id} ${acc.toLogValues()}" } }
            .getOrThrow()
    }

    fun deleteMember(memberId: UUID): QaDeletedRows {
        val deleted = qaMemberEraser.erase(memberId)
        log.info { "qa-test-data.deleteMember memberId=$memberId ${deleted.toLogValues()}" }
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
