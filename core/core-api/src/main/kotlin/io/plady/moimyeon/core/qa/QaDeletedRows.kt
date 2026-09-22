package io.plady.moimyeon.core.qa

data class QaDeletedRows(
    val guestbookPosts: Int = 0,
    val guestbooks: Int = 0,
    val reviewTags: Int = 0,
    val reviews: Int = 0,
    val reviewSkips: Int = 0,
    val attendances: Int = 0,
    val questionVotes: Int = 0,
    val closingResponses: Int = 0,
    val questionComments: Int = 0,
    val answerSummaries: Int = 0,
    val questions: Int = 0,
    val roundFeedbacks: Int = 0,
    val roundAssignments: Int = 0,
    val interviewRounds: Int = 0,
    val interviewPlans: Int = 0,
    val resumeSubmissions: Int = 0,
    val participants: Int = 0,
    val applications: Int = 0,
    val roomStatusLogs: Int = 0,
    val rooms: Int = 0,
) {
    operator fun plus(other: QaDeletedRows): QaDeletedRows = QaDeletedRows(
        guestbookPosts = guestbookPosts + other.guestbookPosts,
        guestbooks = guestbooks + other.guestbooks,
        reviewTags = reviewTags + other.reviewTags,
        reviews = reviews + other.reviews,
        reviewSkips = reviewSkips + other.reviewSkips,
        attendances = attendances + other.attendances,
        questionVotes = questionVotes + other.questionVotes,
        closingResponses = closingResponses + other.closingResponses,
        questionComments = questionComments + other.questionComments,
        answerSummaries = answerSummaries + other.answerSummaries,
        questions = questions + other.questions,
        roundFeedbacks = roundFeedbacks + other.roundFeedbacks,
        roundAssignments = roundAssignments + other.roundAssignments,
        interviewRounds = interviewRounds + other.interviewRounds,
        interviewPlans = interviewPlans + other.interviewPlans,
        resumeSubmissions = resumeSubmissions + other.resumeSubmissions,
        participants = participants + other.participants,
        applications = applications + other.applications,
        roomStatusLogs = roomStatusLogs + other.roomStatusLogs,
        rooms = rooms + other.rooms,
    )

    fun total(): Int = guestbookPosts + guestbooks + reviewTags + reviews + reviewSkips + attendances +
        questionVotes + closingResponses + questionComments + answerSummaries + questions + roundFeedbacks +
        roundAssignments + interviewRounds + interviewPlans + resumeSubmissions + participants + applications +
        roomStatusLogs + rooms

    fun toLogValues(): String = listOf(
        "rooms" to rooms,
        "roomStatusLogs" to roomStatusLogs,
        "applications" to applications,
        "participants" to participants,
        "resumeSubmissions" to resumeSubmissions,
        "interviewPlans" to interviewPlans,
        "interviewRounds" to interviewRounds,
        "roundAssignments" to roundAssignments,
        "roundFeedbacks" to roundFeedbacks,
        "questions" to questions,
        "answerSummaries" to answerSummaries,
        "questionComments" to questionComments,
        "closingResponses" to closingResponses,
        "questionVotes" to questionVotes,
        "attendances" to attendances,
        "reviews" to reviews,
        "reviewTags" to reviewTags,
        "reviewSkips" to reviewSkips,
        "guestbooks" to guestbooks,
        "guestbookPosts" to guestbookPosts,
    ).filter { it.second > 0 }.joinToString(" ") { "${it.first}=${it.second}" }.ifEmpty { "total=0" }

    companion object {
        val NONE = QaDeletedRows()
    }
}
