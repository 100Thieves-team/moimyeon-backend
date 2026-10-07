package io.plady.moimyeon.core.qa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.security.auth.SocialLanding
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
    private val qaDataSweeper: QaDataSweeper,
    private val qaMemberResetter: QaMemberResetter,
    private val qaRoomScheduler: QaRoomScheduler,
    private val qaMemberCreator: QaMemberCreator,
    private val qaResumeSummaryCompleter: QaResumeSummaryCompleter,
    private val qaSocialLogin: QaSocialLogin,
    private val qaMemberStatusChanger: QaMemberStatusChanger,
    private val qaRoomAutoCompleter: QaRoomAutoCompleter,
) {
    fun getQaData(condition: QaDataCondition): QaData = QaData(rooms = qaRoomFinder.getRooms(condition), members = qaMemberFinder.getQaMembers())

    fun deleteRoom(roomId: UUID): QaDeletedRows {
        val deleted = qaRoomEraser.erase(roomId)
        log.info { "qa-test-data.deleteRoom roomId=$roomId ${deleted.toLogValues()}" }
        return deleted
    }

    fun deleteQaData(condition: QaDataCondition): QaDeletedRows {
        val rooms = qaDataSweeper.sweepRooms(condition)
        val members = if (condition.includeMembers) qaDataSweeper.sweepMembers() else QaDeletedRows.NONE
        val deleted = rooms + members
        log.info {
            "qa-test-data.deleteQaData narrowed=${condition.isNarrowed()} hostMemberId=${condition.hostMemberId} " +
                "includeMembers=${condition.includeMembers} ${deleted.toLogValues()}"
        }
        return deleted
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

    fun socialLogin(memberId: UUID): SocialLanding {
        val landing = qaSocialLogin.login(memberId)
        log.info { "qa-test-data.socialLogin memberId=$memberId outcome=${landing.outcome}" }
        return landing
    }

    fun socialSignUp(): SocialLanding {
        val landing = qaSocialLogin.signUp()
        log.info { "qa-test-data.socialSignUp memberId=${landing.memberId} outcome=${landing.outcome}" }
        return landing
    }

    fun changeMemberStatus(memberId: UUID, status: MemberStatus): QaMemberStatus {
        val result = qaMemberStatusChanger.change(memberId, status)
        log.info { "qa-test-data.changeMemberStatus memberId=$memberId before=${result.before} status=${result.status}" }
        return result
    }

    fun autoCompleteRoom(roomId: UUID): QaRoomAutoCompletion {
        val result = qaRoomAutoCompleter.complete(roomId)
        log.info { "qa-test-data.autoCompleteRoom roomId=$roomId completed=${result.completed} status=${result.status}" }
        return result
    }
}
