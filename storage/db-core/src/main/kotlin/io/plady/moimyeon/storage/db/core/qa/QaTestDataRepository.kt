package io.plady.moimyeon.storage.db.core.qa

import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.storage.db.core.RoomEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class QaTestDataRepository(
    private val entityManager: EntityManager,
) {
    fun findRoom(roomId: UUID): RoomEntity? = entityManager.find(RoomEntity::class.java, roomId)

    fun findRoomsByTitlePrefix(prefix: String, hostMemberId: UUID?): List<RoomEntity> {
        val hostFilter = if (hostMemberId == null) "" else " and r.id in ($HOSTED_ROOM_IDS)"
        val query = entityManager.createQuery(
            "select r from RoomEntity r where r.title like :pattern escape '$LIKE_ESCAPE'$hostFilter order by r.createdAt asc, r.id asc",
            RoomEntity::class.java,
        ).setParameter("pattern", likePrefixPattern(prefix))
        hostMemberId?.let { query.setParameter("memberId", it).withHostRole() }
        return query.resultList
    }

    fun findHostedRoomIds(memberId: UUID): List<UUID> = entityManager.createQuery(
        HOSTED_ROOM_IDS,
        UUID::class.java,
    ).setParameter("memberId", memberId).withHostRole().resultList

    fun findHostMemberId(roomId: UUID): UUID? = entityManager.createQuery(
        """
        select p.memberId from ParticipationEntity p
        where p.roomId = :roomId
          and p.participationRole = :role
          and p.status = :status
          and p.deletedAt is null
        order by p.joinedAt asc, p.id asc
        """,
        UUID::class.java,
    ).setParameter("roomId", roomId).withHostRole().setMaxResults(1).resultList.firstOrNull()

    fun countApplications(roomId: UUID): Long = countByRoom("RoomApplicationEntity", roomId)

    fun countParticipations(roomId: UUID): Long = countByRoom("ParticipationEntity", roomId)

    // ---- 룸 그래프 삭제 (자식 → 부모 순으로 호출한다) ----

    fun deleteGuestbookPosts(roomId: UUID): Int = deleteByRoom(
        "delete from GuestbookPostEntity g where g.roomGuestbookId in (select b.id from RoomGuestbookEntity b where b.roomId = :roomId)",
        roomId,
    )

    fun deleteGuestbooks(roomId: UUID): Int = deleteByRoom("delete from RoomGuestbookEntity b where b.roomId = :roomId", roomId)

    fun deleteReviewTags(roomId: UUID): Int = deleteReviewTagsOf(
        entityManager.createQuery("select r.id from ReviewEntity r where r.roomId = :roomId", Long::class.javaObjectType)
            .setParameter("roomId", roomId).resultList.map { it.toLong() },
    )

    fun deleteReviews(roomId: UUID): Int = deleteByRoom("delete from ReviewEntity r where r.roomId = :roomId", roomId)

    fun deleteReviewSkips(roomId: UUID): Int = deleteByRoom("delete from ReviewSkipEntity s where s.roomId = :roomId", roomId)

    fun deleteAttendances(roomId: UUID): Int = deleteByRoom("delete from AttendanceEntity a where a.roomId = :roomId", roomId)

    fun deleteQuestionVotes(roomId: UUID): Int = deleteByRoom(
        "delete from QuestionVoteEntity v where v.questionId in (select q.id from QuestionEntity q where q.roomId = :roomId)",
        roomId,
    )

    fun deleteClosingResponses(roomId: UUID): Int = deleteByRoom("delete from ClosingResponseEntity c where c.roomId = :roomId", roomId)

    fun deleteQuestionComments(roomId: UUID): Int = deleteByRoom(
        "delete from QuestionCommentEntity c where c.questionId in (select q.id from QuestionEntity q where q.roomId = :roomId)",
        roomId,
    )

    fun deleteAnswerSummaries(roomId: UUID): Int = deleteByRoom(
        "delete from AnswerSummaryEntity s where s.questionId in (select q.id from QuestionEntity q where q.roomId = :roomId)",
        roomId,
    )

    fun deleteQuestions(roomId: UUID): Int = deleteByRoom("delete from QuestionEntity q where q.roomId = :roomId", roomId)

    fun deleteRoundFeedbacks(roomId: UUID): Int = deleteByRoom("delete from RoundFeedbackEntity f where f.roomId = :roomId", roomId)

    fun deleteRoundAssignments(roomId: UUID): Int = deleteNativeByRoom(
        """
        delete from round_assignment where interview_round_id in (
            select ir.id from interview_round ir join interview_plan ip on ip.id = ir.interview_plan_id where ip.room_id = :roomId
        )
        """,
        roomId,
    )

    fun deleteInterviewRounds(roomId: UUID): Int = deleteNativeByRoom(
        "delete from interview_round where interview_plan_id in (select ip.id from interview_plan ip where ip.room_id = :roomId)",
        roomId,
    )

    fun deleteInterviewPlans(roomId: UUID): Int = deleteNativeByRoom("delete from interview_plan where room_id = :roomId", roomId)

    fun deleteResumeSubmissions(roomId: UUID): Int = deleteByRoom("delete from ResumeSubmissionEntity s where s.roomId = :roomId", roomId)

    fun deleteParticipations(roomId: UUID): Int = deleteByRoom("delete from ParticipationEntity p where p.roomId = :roomId", roomId)

    fun deleteApplications(roomId: UUID): Int = deleteByRoom("delete from RoomApplicationEntity a where a.roomId = :roomId", roomId)

    fun deleteRoomStatusLogs(roomId: UUID): Int = deleteByRoom("delete from RoomStatusLogEntity l where l.roomId = :roomId", roomId)

    fun deleteRoom(roomId: UUID): Int = deleteByRoom("delete from RoomEntity r where r.id = :roomId", roomId)

    // ---- 회원 단위 삭제 (테스트 계정 초기화) ----

    fun deleteMemberResumeSubmissions(memberId: UUID): Int = deleteByMember("delete from ResumeSubmissionEntity s where s.memberId = :memberId", memberId)

    fun deleteMemberApplications(memberId: UUID): Int = deleteByMember("delete from RoomApplicationEntity a where a.applicantMemberId = :memberId", memberId)

    fun deleteMemberParticipations(memberId: UUID): Int = deleteByMember("delete from ParticipationEntity p where p.memberId = :memberId", memberId)

    fun deleteMemberReviewTags(memberId: UUID, prefix: String): Int = deleteReviewTagsOf(
        entityManager.createQuery(
            "select r.id from ReviewEntity r where (r.authorMemberId = :memberId or r.targetMemberId = :memberId) and r.roomId in ($ROOM_IDS_BY_PREFIX)",
            Long::class.javaObjectType,
        ).setParameter("memberId", memberId).setParameter("pattern", likePrefixPattern(prefix)).resultList.map { it.toLong() },
    )

    fun deleteMemberReviews(memberId: UUID, prefix: String): Int = entityManager.createQuery(
        "delete from ReviewEntity r where (r.authorMemberId = :memberId or r.targetMemberId = :memberId) and r.roomId in ($ROOM_IDS_BY_PREFIX)",
    ).setParameter("memberId", memberId).setParameter("pattern", likePrefixPattern(prefix)).executeUpdate()

    fun deleteMemberReviewSkips(memberId: UUID, prefix: String): Int = entityManager.createQuery(
        "delete from ReviewSkipEntity s where (s.authorMemberId = :memberId or s.targetMemberId = :memberId) and s.roomId in ($ROOM_IDS_BY_PREFIX)",
    ).setParameter("memberId", memberId).setParameter("pattern", likePrefixPattern(prefix)).executeUpdate()

    private fun deleteReviewTagsOf(reviewIds: List<Long>): Int {
        if (reviewIds.isEmpty()) return 0
        return entityManager.createNativeQuery("delete from review_tag where review_id in (:reviewIds)")
            .setParameter("reviewIds", reviewIds)
            .executeUpdate()
    }

    private fun countByRoom(entity: String, roomId: UUID): Long = entityManager.createQuery(
        "select count(e) from $entity e where e.roomId = :roomId",
        Long::class.javaObjectType,
    ).setParameter("roomId", roomId).singleResult.toLong()

    private fun deleteByRoom(jpql: String, roomId: UUID): Int = entityManager.createQuery(jpql).setParameter("roomId", roomId).executeUpdate()

    private fun deleteByMember(jpql: String, memberId: UUID): Int = entityManager.createQuery(jpql).setParameter("memberId", memberId).executeUpdate()

    private fun deleteNativeByRoom(sql: String, roomId: UUID): Int = entityManager.createNativeQuery(sql).setParameter("roomId", roomId).executeUpdate()

    private fun <T> TypedQuery<T>.withHostRole(): TypedQuery<T> = setParameter("role", ParticipationRole.HOST).setParameter("status", ParticipationStatus.JOINED)

    private fun likePrefixPattern(prefix: String): String {
        val escaped = prefix
            .replace(LIKE_ESCAPE, "$LIKE_ESCAPE$LIKE_ESCAPE")
            .replace("%", "$LIKE_ESCAPE%")
            .replace("_", "${LIKE_ESCAPE}_")
        return "$escaped%"
    }

    companion object {
        private const val LIKE_ESCAPE = "!"

        private const val HOSTED_ROOM_IDS = """
            select p.roomId from ParticipationEntity p
            where p.memberId = :memberId
              and p.participationRole = :role
              and p.status = :status
              and p.deletedAt is null
        """

        private const val ROOM_IDS_BY_PREFIX = "select rm.id from RoomEntity rm where rm.title like :pattern escape '$LIKE_ESCAPE'"
    }
}
