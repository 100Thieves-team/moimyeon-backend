package io.plady.moimyeon.core.qa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.storage.db.core.qa.QaTestDataRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
@Profile(DEV_AUTH_PROFILE_EXPRESSION)
class QaMemberResetter(
    private val memberFinder: MemberFinder,
    private val qaTestDataRepository: QaTestDataRepository,
    private val qaRoomEraser: QaRoomEraser,
) {
    @Transactional
    fun reset(memberId: UUID): QaDeletedRows {
        log.debug { "qa-member.resetter.reset memberId=$memberId" }
        memberFinder.getById(memberId)
        return resetRows(memberId)
    }

    // 존재 확인을 호출자가 이미 했을 때(탈퇴한 QA 회원 삭제 포함) 행 정리만 한다.
    @Transactional
    fun resetRows(memberId: UUID): QaDeletedRows {
        val hostedRooms = qaTestDataRepository.findHostedRoomIds(memberId)
            .mapNotNull { qaTestDataRepository.findRoom(it) }
        requireBusiness(hostedRooms.all { QaDataCondition.isQaData(it.title) }, CoreErrorType.QA_DATA_ONLY)

        val erasedRooms = hostedRooms.fold(QaDeletedRows.NONE) { acc, room -> acc + qaRoomEraser.erase(room.id) }
        val prefix = QaDataCondition.QA_MARKER
        val memberRows = QaDeletedRows(
            reviewTags = qaTestDataRepository.deleteMemberReviewTags(memberId, prefix),
            reviews = qaTestDataRepository.deleteMemberReviews(memberId, prefix),
            reviewSkips = qaTestDataRepository.deleteMemberReviewSkips(memberId, prefix),
            resumeSubmissions = qaTestDataRepository.deleteMemberResumeSubmissions(memberId),
            participants = qaTestDataRepository.deleteMemberParticipations(memberId),
            applications = qaTestDataRepository.deleteMemberApplications(memberId),
        )
        return erasedRooms + memberRows
    }
}
