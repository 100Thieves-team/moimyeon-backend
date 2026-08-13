package io.plady.moimyeon.core.domain.trust

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.QuestionCommentType
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.core.AnswerSummaryEntity
import io.plady.moimyeon.storage.db.core.AnswerSummaryRepository
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.QuestionCommentEntity
import io.plady.moimyeon.storage.db.core.QuestionCommentRepository
import io.plady.moimyeon.storage.db.core.QuestionEntity
import io.plady.moimyeon.storage.db.core.QuestionRepository
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Import(FixedTrustClockTestConfiguration::class)
@Transactional
class TrustFinderIT(
    private val trustFinder: TrustFinder,
    private val roomRepository: RoomRepository,
    private val attendanceRepository: AttendanceRepository,
    private val questionRepository: QuestionRepository,
    private val answerSummaryRepository: AnswerSummaryRepository,
    private val questionCommentRepository: QuestionCommentRepository,
    private val reviewRepository: ReviewRepository,
    private val memberRepository: MemberRepository,
    private val entityManager: EntityManager,
) : ContextTest() {
    private val targetMemberId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val recorderMemberId = UUID.fromString("00000000-0000-0000-0000-000000000102")

    @Test
    fun `질문 꼬리질문 답변 요약 질문 피드백을 활동 점수에 포함하고 탈퇴 회원도 모수에 유지한다`() {
        val withdrawnMemberId = UUID.fromString("00000000-0000-0000-0000-000000000103")
        persistWithdrawnMember(withdrawnMemberId)

        val targetRoomId = persistRoom(RoomStatus.COMPLETED, TRUST_NOW.minusDays(3))
        persistAttendance(targetRoomId, targetMemberId, AttendanceStatus.ATTENDED)
        val targetQuestion = persistQuestion(targetRoomId, targetMemberId)
        persistAnswerSummary(targetQuestion.id, targetMemberId)
        persistQuestionComment(targetQuestion.id, targetMemberId)

        repeat(2) { roomIndex ->
            val roomId = persistRoom(RoomStatus.COMPLETED, TRUST_NOW.minusDays((roomIndex + 4).toLong()))
            persistAttendance(roomId, withdrawnMemberId, AttendanceStatus.ATTENDED)
            val rootQuestion = persistQuestion(roomId, withdrawnMemberId)
            repeat(if (roomIndex == 0) 2 else 1) {
                persistQuestion(roomId, withdrawnMemberId, parentQuestionId = rootQuestion.id)
            }
        }

        val trust = trustFinder.getPublicTrust(targetMemberId)

        assertThat(trust.activityTopPercent).isEqualTo(50)
    }

    @Test
    fun `완료되고 삭제되지 않은 룸과 원천만 활동률에 반영한다`() {
        val competitorMemberId = UUID.randomUUID()
        val completedRoomId = persistRoom(RoomStatus.COMPLETED, TRUST_NOW.minusDays(5))
        persistAttendance(completedRoomId, targetMemberId, AttendanceStatus.ATTENDED)
        val activeQuestion = persistQuestion(completedRoomId, targetMemberId)

        val competitorRoomId = persistRoom(RoomStatus.COMPLETED, TRUST_NOW.minusDays(6))
        persistAttendance(competitorRoomId, competitorMemberId, AttendanceStatus.ATTENDED)
        repeat(2) { persistQuestion(competitorRoomId, competitorMemberId) }

        listOf(RoomStatus.IN_PROGRESS, RoomStatus.CANCELED).forEach { status ->
            val roomId = persistRoom(status, TRUST_NOW.minusDays(2))
            persistAttendance(roomId, targetMemberId, AttendanceStatus.ATTENDED)
            repeat(3) { persistQuestion(roomId, targetMemberId) }
        }
        val deletedRoomId = persistRoom(RoomStatus.COMPLETED, TRUST_NOW.minusDays(1), deleted = true)
        persistAttendance(deletedRoomId, targetMemberId, AttendanceStatus.ATTENDED)
        repeat(3) { persistQuestion(deletedRoomId, targetMemberId) }
        val deletedQuestion = persistQuestion(completedRoomId, targetMemberId)
        deletedQuestion.delete(TRUST_NOW.minusHours(1))
        questionRepository.flush()
        val deletedSummary = answerSummaryRepository.saveAndFlush(
            AnswerSummaryEntity(
                questionId = activeQuestion.id,
                authorMemberId = targetMemberId,
                content = "삭제된 답변 요약",
            ),
        )
        deletedSummary.delete(TRUST_NOW.minusMinutes(30))
        answerSummaryRepository.flush()
        val deletedComment = questionCommentRepository.saveAndFlush(
            QuestionCommentEntity(
                questionId = activeQuestion.id,
                authorMemberId = targetMemberId,
                commentType = QuestionCommentType.GOOD_POINT,
                content = "삭제된 질문 피드백",
            ),
        )
        deletedComment.delete(TRUST_NOW.minusMinutes(30))
        questionCommentRepository.flush()
        val deletedAttendanceRoomId = persistRoom(RoomStatus.COMPLETED, TRUST_NOW.minusHours(12))
        val deletedAttendance = persistAttendance(deletedAttendanceRoomId, targetMemberId, AttendanceStatus.ATTENDED)
        deletedAttendance.delete(TRUST_NOW.minusMinutes(10))
        attendanceRepository.flush()
        repeat(3) { persistQuestion(deletedAttendanceRoomId, targetMemberId) }

        val trust = trustFinder.getPublicTrust(targetMemberId)

        assertThat(trust.activityTopPercent).isEqualTo(100)
        assertThat(trust.recentAttendances).containsExactly(AttendanceStatus.ATTENDED)
    }

    @Test
    fun `최근 출석은 시작 시각과 룸 식별자 역순으로 불참을 포함해 3건 반환하고 불참은 누적한다`() {
        val oldestRoomId = UUID.fromString("00000000-0000-0000-0000-000000000201")
        val tiedLowerRoomId = UUID.fromString("00000000-0000-0000-0000-000000000202")
        val tiedHigherRoomId = UUID.fromString("00000000-0000-0000-0000-000000000203")
        val newestRoomId = UUID.fromString("00000000-0000-0000-0000-000000000204")
        persistRoom(RoomStatus.COMPLETED, TRUST_NOW.minusDays(4), id = oldestRoomId)
        persistRoom(RoomStatus.COMPLETED, TRUST_NOW.minusDays(2), id = tiedLowerRoomId)
        persistRoom(RoomStatus.COMPLETED, TRUST_NOW.minusDays(2), id = tiedHigherRoomId)
        persistRoom(RoomStatus.COMPLETED, TRUST_NOW.minusDays(1), id = newestRoomId)
        persistAttendance(oldestRoomId, targetMemberId, AttendanceStatus.ABSENT)
        persistAttendance(tiedLowerRoomId, targetMemberId, AttendanceStatus.ATTENDED)
        persistAttendance(tiedHigherRoomId, targetMemberId, AttendanceStatus.ABSENT)
        persistAttendance(newestRoomId, targetMemberId, AttendanceStatus.ATTENDED)

        val trust = trustFinder.getPublicTrust(targetMemberId)

        assertThat(trust.recentAttendances).containsExactly(
            AttendanceStatus.ATTENDED,
            AttendanceStatus.ABSENT,
            AttendanceStatus.ATTENDED,
        )
        assertThat(trust.noShowCount).isEqualTo(2)
    }

    @Test
    fun `대표 태그는 공개 시각과 숨김 삭제 상태를 반영해 횟수와 문구 순 상위 3개를 반환한다`() {
        persistReview(tags = listOf("Bravo", "Alpha"), visibleAt = TRUST_NOW)
        persistReview(tags = listOf("Bravo", "Alpha"))
        persistReview(tags = listOf("Delta"))
        persistReview(tags = listOf("Charlie"))
        persistReview(tags = listOf("Future", "Future2"), visibleAt = TRUST_NOW.plusSeconds(1))
        persistReview(tags = listOf("Hidden", "Hidden2"), hiddenAt = TRUST_NOW.minusMinutes(1))
        persistReview(tags = listOf("Deleted", "Deleted2"), deleted = true)

        val trust = trustFinder.getPublicTrust(targetMemberId)

        assertThat(trust.representativeTags).containsExactly(
            RepresentativeTag(label = "Alpha", count = 2),
            RepresentativeTag(label = "Bravo", count = 2),
            RepresentativeTag(label = "Charlie", count = 1),
        )
    }

    private fun persistWithdrawnMember(memberId: UUID) {
        val member = memberRepository.saveAndFlush(
            MemberEntity(
                id = memberId,
                email = "withdrawn@example.com",
                nickname = "탈퇴회원101",
                status = MemberStatus.ACTIVE,
                lastLoginAt = TRUST_NOW.minusDays(10),
                socialAccounts = listOf(
                    SocialAccountEntity(
                        provider = SocialLoginProvider.GOOGLE,
                        providerId = "withdrawn-public-trust",
                        linkedEmail = "withdrawn@example.com",
                    ),
                ),
            ),
        )
        member.delete(TRUST_NOW.minusDays(1))
        memberRepository.flush()
    }

    private fun persistRoom(
        status: RoomStatus,
        startAt: LocalDateTime,
        id: UUID = UUID.randomUUID(),
        deleted: Boolean = false,
    ): UUID {
        val room = roomRepository.saveAndFlush(
            RoomEntity(
                id = id,
                jobPostingId = 1L,
                jobRoleId = 1L,
                resumePublic = false,
                sigunguId = null,
                title = "신뢰 지표 테스트 룸",
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 4,
                startAt = startAt,
                durationMinutes = 60,
            ),
        )
        if (deleted) {
            room.delete(TRUST_NOW.minusMinutes(1))
            roomRepository.flush()
        }
        entityManager.createNativeQuery("update room set status = :status where id = :roomId")
            .setParameter("status", status.name)
            .setParameter("roomId", id)
            .executeUpdate()
        entityManager.clear()
        return id
    }

    private fun persistAttendance(roomId: UUID, memberId: UUID, status: AttendanceStatus): AttendanceEntity {
        return attendanceRepository.saveAndFlush(
            AttendanceEntity(
                roomId = roomId,
                memberId = memberId,
                status = status,
                recorderMemberId = recorderMemberId,
                recordedAt = TRUST_NOW.minusHours(1),
            ),
        )
    }

    private fun persistQuestion(
        roomId: UUID,
        authorMemberId: UUID,
        parentQuestionId: Long? = null,
    ): QuestionEntity {
        return questionRepository.saveAndFlush(
            QuestionEntity(
                roomId = roomId,
                targetMemberId = recorderMemberId,
                authorMemberId = authorMemberId,
                parentQuestionId = parentQuestionId,
                content = "질문",
                source = QuestionSource.IN_PROGRESS,
                asked = true,
            ),
        )
    }

    private fun persistAnswerSummary(questionId: Long, authorMemberId: UUID) {
        answerSummaryRepository.saveAndFlush(
            AnswerSummaryEntity(
                questionId = questionId,
                authorMemberId = authorMemberId,
                content = "답변 요약",
            ),
        )
    }

    private fun persistQuestionComment(questionId: Long, authorMemberId: UUID) {
        questionCommentRepository.saveAndFlush(
            QuestionCommentEntity(
                questionId = questionId,
                authorMemberId = authorMemberId,
                commentType = QuestionCommentType.GOOD_POINT,
                content = "질문 피드백",
            ),
        )
    }

    private fun persistReview(
        tags: List<String>,
        visibleAt: LocalDateTime = TRUST_NOW.minusMinutes(1),
        hiddenAt: LocalDateTime? = null,
        deleted: Boolean = false,
    ) {
        val review = reviewRepository.saveAndFlush(
            ReviewEntity(
                roomId = UUID.randomUUID(),
                authorMemberId = UUID.randomUUID(),
                targetMemberId = targetMemberId,
                visibleAt = visibleAt,
                hiddenAt = hiddenAt,
                tags = tags,
            ),
        )
        if (deleted) {
            review.delete(TRUST_NOW.minusSeconds(1))
            reviewRepository.flush()
        }
    }
}
