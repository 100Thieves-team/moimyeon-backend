package io.plady.moimyeon.storage.db.core.qa

import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.RoomCount
import io.plady.moimyeon.storage.db.core.RoomEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
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

    fun findHostMemberIds(roomIds: Collection<UUID>): Map<UUID, UUID> {
        if (roomIds.isEmpty()) return emptyMap()
        return entityManager.createQuery(
            """
            select p.roomId, p.memberId from ParticipationEntity p
            where p.roomId in (:roomIds)
              and p.participationRole = :role
              and p.status = :status
              and p.deletedAt is null
            order by p.joinedAt asc, p.id asc
            """,
            Array<Any>::class.java,
        ).setParameter("roomIds", roomIds).withHostRole().resultList
            .groupBy({ it[0] as UUID }, { it[1] as UUID })
            .mapValues { it.value.first() }
    }

    fun countApplicationsByRoomIds(roomIds: Collection<UUID>): List<RoomCount> = countByRoomIds("RoomApplicationEntity", roomIds)

    fun countParticipationsByRoomIds(roomIds: Collection<UUID>): List<RoomCount> = countByRoomIds("ParticipationEntity", roomIds)

    // ---- 룸 일정 ----

    fun updateRoomStartAt(roomId: UUID, startAt: LocalDateTime): Int = entityManager
        .createQuery("update RoomEntity r set r.startAt = :startAt where r.id = :roomId")
        .setParameter("startAt", startAt)
        .setParameter("roomId", roomId)
        .executeUpdate()

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

    // ---- QA 생성 회원 ----

    fun findMember(memberId: UUID): MemberEntity? = entityManager.find(MemberEntity::class.java, memberId)

    fun findQaMembers(providerIdPrefix: String, emailDomain: String): List<MemberEntity> = entityManager.createQuery(
        "select distinct m from MemberEntity m join m.socialAccounts s where $QA_MEMBER_PREDICATE order by m.createdAt asc, m.id asc",
        MemberEntity::class.java,
    ).withQaMemberPatterns(providerIdPrefix, emailDomain).resultList

    fun isQaMember(memberId: UUID, providerIdPrefix: String, emailDomain: String): Boolean = entityManager.createQuery(
        "select count(m) from MemberEntity m join m.socialAccounts s where m.id = :memberId and $QA_MEMBER_PREDICATE",
        Long::class.javaObjectType,
    ).setParameter("memberId", memberId).withQaMemberPatterns(providerIdPrefix, emailDomain).singleResult > 0

    fun findMemberQuestionIds(memberId: UUID): List<Long> = entityManager.createQuery(
        "select q.id from QuestionEntity q where q.authorMemberId = :memberId or q.targetMemberId = :memberId",
        Long::class.javaObjectType,
    ).setParameter("memberId", memberId).resultList.map { it.toLong() }

    fun findFollowUpQuestionIds(parentIds: Collection<Long>): List<Long> {
        if (parentIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            "select q.id from QuestionEntity q where q.parentQuestionId in (:ids)",
            Long::class.javaObjectType,
        ).setParameter("ids", parentIds).resultList.map { it.toLong() }
    }

    fun deleteMemberClosingResponseVotes(memberId: UUID): Int = deleteNativeByMember(
        "delete from question_vote where closing_response_id in (select id from closing_response where member_id = :memberId)",
        memberId,
    )

    fun deleteQuestionVotesByQuestionIds(questionIds: Collection<Long>): Int = deleteByIds("delete from QuestionVoteEntity v where v.questionId in (:ids)", questionIds)

    fun deleteMemberClosingResponses(memberId: UUID): Int = deleteByMember("delete from ClosingResponseEntity c where c.memberId = :memberId", memberId)

    fun deleteQuestionCommentsByQuestionIds(questionIds: Collection<Long>): Int = deleteByIds("delete from QuestionCommentEntity c where c.questionId in (:ids)", questionIds)

    fun deleteMemberAuthoredQuestionComments(memberId: UUID): Int = deleteByMember("delete from QuestionCommentEntity c where c.authorMemberId = :memberId", memberId)

    fun deleteAnswerSummariesByQuestionIds(questionIds: Collection<Long>): Int = deleteByIds("delete from AnswerSummaryEntity s where s.questionId in (:ids)", questionIds)

    fun deleteMemberAuthoredAnswerSummaries(memberId: UUID): Int = deleteByMember("delete from AnswerSummaryEntity s where s.authorMemberId = :memberId", memberId)

    fun deleteQuestionsByIds(questionIds: Collection<Long>): Int = deleteByIds("delete from QuestionEntity q where q.id in (:ids)", questionIds)

    fun deleteMemberRoundAssignments(memberId: UUID): Int = deleteNativeByMember("delete from round_assignment where member_id = :memberId", memberId)

    fun clearMemberInterviewRounds(memberId: UUID): Int = entityManager
        .createNativeQuery("update interview_round set interviewee_member_id = null where interviewee_member_id = :memberId")
        .setParameter("memberId", memberId)
        .executeUpdate()

    fun deleteMemberRoundFeedbacks(memberId: UUID): Int = deleteByMember(
        "delete from RoundFeedbackEntity f where f.authorMemberId = :memberId or f.intervieweeMemberId = :memberId",
        memberId,
    )

    fun deleteMemberGuestbookPosts(memberId: UUID): Int = deleteByMember("delete from GuestbookPostEntity g where g.authorMemberId = :memberId", memberId)

    fun deleteMemberAttendances(memberId: UUID): Int = deleteByMember("delete from AttendanceEntity a where a.memberId = :memberId", memberId)

    fun deleteMemberReviewTagsInAllRooms(memberId: UUID): Int = deleteReviewTagsOf(
        entityManager.createQuery(
            "select r.id from ReviewEntity r where r.authorMemberId = :memberId or r.targetMemberId = :memberId",
            Long::class.javaObjectType,
        ).setParameter("memberId", memberId).resultList.map { it.toLong() },
    )

    fun deleteMemberReviewsInAllRooms(memberId: UUID): Int = deleteByMember("delete from ReviewEntity r where r.authorMemberId = :memberId or r.targetMemberId = :memberId", memberId)

    fun deleteMemberReviewSkipsInAllRooms(memberId: UUID): Int = deleteByMember("delete from ReviewSkipEntity s where s.authorMemberId = :memberId or s.targetMemberId = :memberId", memberId)

    fun deleteMemberResumes(memberId: UUID): Int = deleteByMember("delete from ResumeEntity r where r.memberId = :memberId", memberId)

    fun deleteMemberProfileInterests(memberId: UUID): Int = deleteNativeByMember(
        "delete from member_profile_interest_company where profile_id in (select id from member_profile where member_id = :memberId)",
        memberId,
    ) +
        deleteNativeByMember(
            "delete from member_profile_interest_job_role where profile_id in (select id from member_profile where member_id = :memberId)",
            memberId,
        )

    fun deleteMemberProfile(memberId: UUID): Int = deleteByMember("delete from MemberProfileEntity p where p.memberId = :memberId", memberId)

    fun deleteMemberTermsAgreements(memberId: UUID): Int = deleteByMember("delete from TermsAgreementEntity t where t.memberId = :memberId", memberId)

    fun deleteMemberRefreshTokens(memberId: UUID): Int = deleteByMember("delete from RefreshTokenEntity t where t.memberId = :memberId", memberId)

    fun deleteMemberWebPushSubscriptions(memberId: UUID): Int = deleteByMember("delete from WebPushSubscriptionEntity w where w.memberId = :memberId", memberId)

    fun deleteMemberSocialAccounts(memberId: UUID): Int = deleteNativeByMember("delete from social_account where member_id = :memberId", memberId)

    fun deleteMember(memberId: UUID): Int = deleteByMember("delete from MemberEntity m where m.id = :memberId", memberId)

    // ---- 회원 단위 삭제 (테스트 계정 초기화) ----

    fun deleteMemberResumeSubmissions(memberId: UUID): Int = deleteByMember("delete from ResumeSubmissionEntity s where s.memberId = :memberId", memberId)

    fun deleteMemberApplications(memberId: UUID): Int = deleteByMember("delete from RoomApplicationEntity a where a.applicantMemberId = :memberId", memberId)

    fun deleteMemberParticipations(memberId: UUID): Int = deleteByMember("delete from ParticipationEntity p where p.memberId = :memberId", memberId)

    fun deleteMemberReviewTagsInQaRooms(memberId: UUID, prefix: String): Int = deleteReviewTagsOf(
        entityManager.createQuery(
            "select r.id from ReviewEntity r where (r.authorMemberId = :memberId or r.targetMemberId = :memberId) and r.roomId in ($ROOM_IDS_BY_PREFIX)",
            Long::class.javaObjectType,
        ).setParameter("memberId", memberId).setParameter("pattern", likePrefixPattern(prefix)).resultList.map { it.toLong() },
    )

    fun deleteMemberReviewsInQaRooms(memberId: UUID, prefix: String): Int = entityManager.createQuery(
        "delete from ReviewEntity r where (r.authorMemberId = :memberId or r.targetMemberId = :memberId) and r.roomId in ($ROOM_IDS_BY_PREFIX)",
    ).setParameter("memberId", memberId).setParameter("pattern", likePrefixPattern(prefix)).executeUpdate()

    fun deleteMemberReviewSkipsInQaRooms(memberId: UUID, prefix: String): Int = entityManager.createQuery(
        "delete from ReviewSkipEntity s where (s.authorMemberId = :memberId or s.targetMemberId = :memberId) and s.roomId in ($ROOM_IDS_BY_PREFIX)",
    ).setParameter("memberId", memberId).setParameter("pattern", likePrefixPattern(prefix)).executeUpdate()

    private fun deleteReviewTagsOf(reviewIds: List<Long>): Int {
        if (reviewIds.isEmpty()) return 0
        return entityManager.createNativeQuery("delete from review_tag where review_id in (:reviewIds)")
            .setParameter("reviewIds", reviewIds)
            .executeUpdate()
    }

    private fun countByRoomIds(entity: String, roomIds: Collection<UUID>): List<RoomCount> {
        if (roomIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            "select new io.plady.moimyeon.storage.db.core.RoomCount(e.roomId, count(e)) from $entity e where e.roomId in (:roomIds) group by e.roomId",
            RoomCount::class.java,
        ).setParameter("roomIds", roomIds).resultList
    }

    private fun deleteByRoom(jpql: String, roomId: UUID): Int = entityManager.createQuery(jpql).setParameter("roomId", roomId).executeUpdate()

    private fun deleteByIds(jpql: String, ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return entityManager.createQuery(jpql).setParameter("ids", ids).executeUpdate()
    }

    private fun deleteByMember(jpql: String, memberId: UUID): Int = entityManager.createQuery(jpql).setParameter("memberId", memberId).executeUpdate()

    private fun deleteNativeByRoom(sql: String, roomId: UUID): Int = entityManager.createNativeQuery(sql).setParameter("roomId", roomId).executeUpdate()

    private fun deleteNativeByMember(sql: String, memberId: UUID): Int = entityManager.createNativeQuery(sql).setParameter("memberId", memberId).executeUpdate()

    private fun <T> TypedQuery<T>.withQaMemberPatterns(providerIdPrefix: String, emailDomain: String): TypedQuery<T> = setParameter("providerPattern", likePrefixPattern(providerIdPrefix))
        .setParameter("emailPattern", "%@${escapeLike(emailDomain)}")

    private fun <T> TypedQuery<T>.withHostRole(): TypedQuery<T> = setParameter("role", ParticipationRole.HOST).setParameter("status", ParticipationStatus.JOINED)

    private fun likePrefixPattern(prefix: String): String = "${escapeLike(prefix)}%"

    private fun escapeLike(text: String): String = text
        .replace(LIKE_ESCAPE, "$LIKE_ESCAPE$LIKE_ESCAPE")
        .replace("%", "$LIKE_ESCAPE%")
        .replace("_", "${LIKE_ESCAPE}_")

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

        private const val QA_MEMBER_PREDICATE =
            "s.providerId like :providerPattern escape '$LIKE_ESCAPE' and m.email like :emailPattern escape '$LIKE_ESCAPE'"
    }
}
