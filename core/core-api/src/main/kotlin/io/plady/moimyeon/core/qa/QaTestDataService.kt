package io.plady.moimyeon.core.qa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
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
        val rooms = eraseQaRooms(condition)
        val members = if (condition.includeMembers) eraseQaMembers() else QaDeletedRows.NONE
        val deleted = rooms + members
        log.info {
            "qa-test-data.deleteQaData narrowed=${condition.isNarrowed()} hostMemberId=${condition.hostMemberId} " +
                "includeMembers=${condition.includeMembers} ${deleted.toLogValues()}"
        }
        return deleted
    }

    // 룸 하나가 한 트랜잭션. 하나가 실패해도 앞선 룸은 지워진 채 남고 실패 룸을 로그로 남긴다.
    // 목록 조회와 삭제 사이에 다른 요청이 먼저 지운 룸(E1405)은 이미 목표 상태이므로 건너뛴다.
    private fun eraseQaRooms(condition: QaDataCondition): QaDeletedRows = qaRoomFinder.getRoomIds(condition).fold(QaDeletedRows.NONE) { acc, roomId ->
        acc + runCatching { qaRoomEraser.erase(roomId) }
            .recoverCatching { it.alreadyGoneOr(CoreErrorType.ROOM_NOT_FOUND) }
            .onFailure { log.warn(it) { "qa-test-data.deleteQaData.roomFailed roomId=$roomId ${acc.toLogValues()}" } }
            .getOrThrow()
    }

    // 회원 한 명이 한 트랜잭션. 위와 같은 규칙이며 먼저 지워진 회원(E1006)은 건너뛴다.
    private fun eraseQaMembers(): QaDeletedRows = qaMemberFinder.getQaMembers().fold(QaDeletedRows.NONE) { acc, member ->
        acc + runCatching { qaMemberEraser.erase(member.id) }
            .recoverCatching { it.alreadyGoneOr(CoreErrorType.MEMBER_NOT_FOUND) }
            .onFailure { log.warn(it) { "qa-test-data.deleteQaData.memberFailed memberId=${member.id} ${acc.toLogValues()}" } }
            .getOrThrow()
    }

    private fun Throwable.alreadyGoneOr(notFound: CoreErrorType): QaDeletedRows = if (this is CoreException && errorType == notFound) QaDeletedRows.NONE else throw this

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
