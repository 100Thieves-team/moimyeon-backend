package io.plady.moimyeon.core.qa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.plady.moimyeon.core.api.auth.DEV_AUTH_PROFILE_EXPRESSION
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.qa.QaTestDataRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
@Profile(DEV_AUTH_PROFILE_EXPRESSION)
class QaRoomEraser(
    private val qaTestDataRepository: QaTestDataRepository,
) {
    @Transactional
    fun erase(roomId: UUID): QaDeletedRows {
        log.debug { "qa-room.eraser.erase roomId=$roomId" }
        val room = requireFound(qaTestDataRepository.findRoom(roomId), CoreErrorType.ROOM_NOT_FOUND)
        requireBusiness(QaDataCondition.isQaData(room.title), CoreErrorType.QA_DATA_ONLY)
        return eraseGraph(room)
    }

    // 자식 → 부모 순서. FK 제약이 없어 순서는 코드가 지킨다.
    private fun eraseGraph(room: RoomEntity): QaDeletedRows {
        check(QaDataCondition.isQaData(room.title)) { "QA 마커가 없는 룸은 지울 수 없다" }
        val roomId = room.id
        return QaDeletedRows(
            guestbookPosts = qaTestDataRepository.deleteGuestbookPosts(roomId),
            guestbooks = qaTestDataRepository.deleteGuestbooks(roomId),
            reviewTags = qaTestDataRepository.deleteReviewTags(roomId),
            reviews = qaTestDataRepository.deleteReviews(roomId),
            reviewSkips = qaTestDataRepository.deleteReviewSkips(roomId),
            attendances = qaTestDataRepository.deleteAttendances(roomId),
            questionVotes = qaTestDataRepository.deleteQuestionVotes(roomId),
            closingResponses = qaTestDataRepository.deleteClosingResponses(roomId),
            questionComments = qaTestDataRepository.deleteQuestionComments(roomId),
            answerSummaries = qaTestDataRepository.deleteAnswerSummaries(roomId),
            questions = qaTestDataRepository.deleteQuestions(roomId),
            roundFeedbacks = qaTestDataRepository.deleteRoundFeedbacks(roomId),
            roundAssignments = qaTestDataRepository.deleteRoundAssignments(roomId),
            interviewRounds = qaTestDataRepository.deleteInterviewRounds(roomId),
            interviewPlans = qaTestDataRepository.deleteInterviewPlans(roomId),
            resumeSubmissions = qaTestDataRepository.deleteResumeSubmissions(roomId),
            participants = qaTestDataRepository.deleteParticipations(roomId),
            applications = qaTestDataRepository.deleteApplications(roomId),
            roomStatusLogs = qaTestDataRepository.deleteRoomStatusLogs(roomId),
            rooms = qaTestDataRepository.deleteRoom(roomId),
        )
    }
}
