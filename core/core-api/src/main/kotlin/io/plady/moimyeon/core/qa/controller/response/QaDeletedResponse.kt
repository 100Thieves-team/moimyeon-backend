package io.plady.moimyeon.core.qa.controller.response

import io.plady.moimyeon.core.qa.QaDeletedRows

data class QaDeletedResponse(
    val deleted: QaDeletedRowsResponse,
) {
    companion object {
        fun from(rows: QaDeletedRows): QaDeletedResponse = QaDeletedResponse(QaDeletedRowsResponse.from(rows))
    }
}

data class QaDeletedRowsResponse(
    val rooms: Int,
    val roomStatusLogs: Int,
    val applications: Int,
    val participants: Int,
    val resumeSubmissions: Int,
    val interviewPlans: Int,
    val interviewRounds: Int,
    val roundAssignments: Int,
    val roundFeedbacks: Int,
    val questions: Int,
    val answerSummaries: Int,
    val questionComments: Int,
    val closingResponses: Int,
    val questionVotes: Int,
    val attendances: Int,
    val reviews: Int,
    val reviewTags: Int,
    val reviewSkips: Int,
    val guestbooks: Int,
    val guestbookPosts: Int,
    val resumes: Int,
    val profiles: Int,
    val termsAgreements: Int,
    val refreshTokens: Int,
    val webPushSubscriptions: Int,
    val socialAccounts: Int,
    val members: Int,
    val total: Int,
) {
    companion object {
        fun from(rows: QaDeletedRows): QaDeletedRowsResponse = QaDeletedRowsResponse(
            rooms = rows.rooms,
            roomStatusLogs = rows.roomStatusLogs,
            applications = rows.applications,
            participants = rows.participants,
            resumeSubmissions = rows.resumeSubmissions,
            interviewPlans = rows.interviewPlans,
            interviewRounds = rows.interviewRounds,
            roundAssignments = rows.roundAssignments,
            roundFeedbacks = rows.roundFeedbacks,
            questions = rows.questions,
            answerSummaries = rows.answerSummaries,
            questionComments = rows.questionComments,
            closingResponses = rows.closingResponses,
            questionVotes = rows.questionVotes,
            attendances = rows.attendances,
            reviews = rows.reviews,
            reviewTags = rows.reviewTags,
            reviewSkips = rows.reviewSkips,
            guestbooks = rows.guestbooks,
            guestbookPosts = rows.guestbookPosts,
            resumes = rows.resumes,
            profiles = rows.profiles,
            termsAgreements = rows.termsAgreements,
            refreshTokens = rows.refreshTokens,
            webPushSubscriptions = rows.webPushSubscriptions,
            socialAccounts = rows.socialAccounts,
            members = rows.members,
            total = rows.total(),
        )
    }
}
