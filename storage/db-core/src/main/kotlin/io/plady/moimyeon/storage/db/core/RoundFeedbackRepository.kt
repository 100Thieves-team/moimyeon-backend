package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.RoundFeedbackType
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.util.UUID

interface RoundFeedbackRepository : JpaRepository<RoundFeedbackEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateByRoomIdAndIntervieweeMemberIdAndAuthorMemberIdAndDeletedAtIsNull(
        roomId: UUID,
        intervieweeMemberId: UUID,
        authorMemberId: UUID,
    ): RoundFeedbackEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateByRoomIdAndIntervieweeMemberIdAndIdAndFeedbackTypeAndDeletedAtIsNull(
        roomId: UUID,
        intervieweeMemberId: UUID,
        id: Long,
        feedbackType: RoundFeedbackType,
    ): RoundFeedbackEntity?

    fun findAllByRoomIdAndIntervieweeMemberIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
        roomId: UUID,
        intervieweeMemberId: UUID,
    ): List<RoundFeedbackEntity>
}
