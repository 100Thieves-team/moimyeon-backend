package io.plady.moimyeon.core.qa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.qa.QaTestDataRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
@Profile(DEV_AUTH_PROFILE_EXPRESSION)
class QaMemberEraser(
    private val qaTestDataRepository: QaTestDataRepository,
    private val qaMemberResetter: QaMemberResetter,
) {
    @Transactional
    fun erase(memberId: UUID): QaDeletedRows {
        log.debug { "qa-member.eraser.erase memberId=$memberId" }
        requireFound(qaTestDataRepository.findMember(memberId), CoreErrorType.MEMBER_NOT_FOUND)
        requireBusiness(isQaMember(memberId), CoreErrorType.QA_DATA_ONLY)
        return qaMemberResetter.resetRows(memberId) + eraseOwnedRows(memberId)
    }

    private fun isQaMember(memberId: UUID): Boolean = qaTestDataRepository.isQaMember(memberId, QaMemberCreator.PROVIDER_ID_PREFIX, QaMemberCreator.EMAIL_DOMAIN)

    // 자식 → 부모 순서. 회원이 남긴 행 → 회원 소유 행 → 회원. 남이 단 꼬리질문은 부모 질문과 함께 지운다.
    private fun eraseOwnedRows(memberId: UUID): QaDeletedRows {
        check(isQaMember(memberId)) { "QA 생성 회원이 아니면 지울 수 없다" }
        val memberQuestionIds = qaTestDataRepository.findMemberQuestionIds(memberId)
        val questionIds = memberQuestionIds + qaTestDataRepository.findFollowUpQuestionIds(memberQuestionIds)
        val questionVotes = qaTestDataRepository.deleteMemberClosingResponseVotes(memberId) +
            qaTestDataRepository.deleteQuestionVotesByQuestionIds(questionIds)
        val closingResponses = qaTestDataRepository.deleteMemberClosingResponses(memberId)
        val questionComments = qaTestDataRepository.deleteQuestionCommentsByQuestionIds(questionIds) +
            qaTestDataRepository.deleteMemberAuthoredQuestionComments(memberId)
        val answerSummaries = qaTestDataRepository.deleteAnswerSummariesByQuestionIds(questionIds) +
            qaTestDataRepository.deleteMemberAuthoredAnswerSummaries(memberId)
        val questions = qaTestDataRepository.deleteQuestionsByIds(questionIds)
        val roundFeedbacks = qaTestDataRepository.deleteMemberRoundFeedbacks(memberId)
        val roundAssignments = qaTestDataRepository.deleteMemberRoundAssignments(memberId)
        qaTestDataRepository.clearMemberInterviewRounds(memberId)
        val guestbookPosts = qaTestDataRepository.deleteMemberGuestbookPosts(memberId)
        val attendances = qaTestDataRepository.deleteMemberAttendances(memberId)
        val reviewTags = qaTestDataRepository.deleteMemberReviewTagsInAllRooms(memberId)
        val reviews = qaTestDataRepository.deleteMemberReviewsInAllRooms(memberId)
        val reviewSkips = qaTestDataRepository.deleteMemberReviewSkipsInAllRooms(memberId)
        val resumeSubmissions = qaTestDataRepository.deleteMemberResumeSubmissions(memberId)
        val resumes = qaTestDataRepository.deleteMemberResumes(memberId)
        val profiles = qaTestDataRepository.deleteMemberProfileInterests(memberId) + qaTestDataRepository.deleteMemberProfile(memberId)
        val termsAgreements = qaTestDataRepository.deleteMemberTermsAgreements(memberId)
        val refreshTokens = qaTestDataRepository.deleteMemberRefreshTokens(memberId)
        val webPushSubscriptions = qaTestDataRepository.deleteMemberWebPushSubscriptions(memberId)
        val socialAccounts = qaTestDataRepository.deleteMemberSocialAccounts(memberId)
        val members = qaTestDataRepository.deleteMember(memberId)
        return QaDeletedRows(
            questionVotes = questionVotes,
            closingResponses = closingResponses,
            questionComments = questionComments,
            answerSummaries = answerSummaries,
            questions = questions,
            roundFeedbacks = roundFeedbacks,
            roundAssignments = roundAssignments,
            guestbookPosts = guestbookPosts,
            attendances = attendances,
            reviewTags = reviewTags,
            reviews = reviews,
            reviewSkips = reviewSkips,
            resumeSubmissions = resumeSubmissions,
            resumes = resumes,
            profiles = profiles,
            termsAgreements = termsAgreements,
            refreshTokens = refreshTokens,
            webPushSubscriptions = webPushSubscriptions,
            socialAccounts = socialAccounts,
            members = members,
        )
    }
}
