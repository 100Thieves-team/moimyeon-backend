package io.plady.moimyeon.core.qa

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.QuestionCommentType
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.core.enums.QuestionVote
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.enums.RoomStatus
import io.plady.moimyeon.core.enums.RoundFeedbackType
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.AnswerSummaryEntity
import io.plady.moimyeon.storage.db.core.AnswerSummaryRepository
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.ClosingResponseEntity
import io.plady.moimyeon.storage.db.core.ClosingResponseRepository
import io.plady.moimyeon.storage.db.core.GuestbookPostEntity
import io.plady.moimyeon.storage.db.core.GuestbookPostRepository
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.QuestionCommentEntity
import io.plady.moimyeon.storage.db.core.QuestionCommentRepository
import io.plady.moimyeon.storage.db.core.QuestionEntity
import io.plady.moimyeon.storage.db.core.QuestionRepository
import io.plady.moimyeon.storage.db.core.QuestionVoteEntity
import io.plady.moimyeon.storage.db.core.ResumeSubmissionEntity
import io.plady.moimyeon.storage.db.core.ResumeSubmissionRepository
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import io.plady.moimyeon.storage.db.core.ReviewSkipEntity
import io.plady.moimyeon.storage.db.core.ReviewSkipRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomGuestbookEntity
import io.plady.moimyeon.storage.db.core.RoomGuestbookRepository
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.RoomStatusLogEntity
import io.plady.moimyeon.storage.db.core.RoomStatusLogRepository
import io.plady.moimyeon.storage.db.core.RoundFeedbackEntity
import io.plady.moimyeon.storage.db.core.RoundFeedbackRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.util.UUID

class QaRoomEraserIT(
    private val qaRoomEraser: QaRoomEraser,
    private val qaRoomFinder: QaRoomFinder,
    private val jdbcTemplate: JdbcTemplate,
    private val roomRepository: RoomRepository,
    private val memberRepository: MemberRepository,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val roomStatusLogRepository: RoomStatusLogRepository,
    private val resumeSubmissionRepository: ResumeSubmissionRepository,
    private val attendanceRepository: AttendanceRepository,
    private val questionRepository: QuestionRepository,
    private val answerSummaryRepository: AnswerSummaryRepository,
    private val questionCommentRepository: QuestionCommentRepository,
    private val closingResponseRepository: ClosingResponseRepository,
    private val roundFeedbackRepository: RoundFeedbackRepository,
    private val reviewRepository: ReviewRepository,
    private val reviewSkipRepository: ReviewSkipRepository,
    private val roomGuestbookRepository: RoomGuestbookRepository,
    private val guestbookPostRepository: GuestbookPostRepository,
) : ContextTest() {
    private val qaRoomId = UUID.randomUUID()
    private val otherQaRoomId = UUID.randomUUID()
    private val liveRoomId = UUID.randomUUID()
    private val hostId = UUID.randomUUID()
    private val guestId = UUID.randomUUID()
    private val at = LocalDateTime.of(2026, 9, 22, 10, 0)
    private val seededRoomIds = listOf(qaRoomId, otherQaRoomId, liveRoomId)

    @AfterEach
    fun cleanUp() {
        seededRoomIds.forEach { roomId ->
            ROOM_CHILD_TABLES.forEach { (table, sql) -> jdbcTemplate.update(sql, *bindings(table, roomId)) }
            jdbcTemplate.update("delete from room where id = ?", bytes(roomId))
        }
        memberRepository.deleteAllById(listOf(hostId, guestId))
    }

    @Test
    fun `QA 룸을 딸린 행까지 전부 지우고 테이블별 건수를 돌려준다`() {
        seedMembers()
        seedRoom(liveRoomId, "면접 스터디 (실데이터)")
        seedGraph(liveRoomId)
        seedRoom(qaRoomId, "[QA] 삭제 대상 룸")
        seedGraph(qaRoomId)

        val deleted = qaRoomEraser.erase(qaRoomId)

        assertThat(deleted).isEqualTo(
            QaDeletedRows(
                guestbookPosts = 2,
                guestbooks = 1,
                reviewTags = 2,
                reviews = 1,
                reviewSkips = 1,
                attendances = 2,
                questionVotes = 1,
                closingResponses = 1,
                questionComments = 1,
                answerSummaries = 1,
                questions = 2,
                roundFeedbacks = 1,
                roundAssignments = 1,
                interviewRounds = 1,
                interviewPlans = 1,
                resumeSubmissions = 1,
                participants = 2,
                applications = 1,
                roomStatusLogs = 1,
                rooms = 1,
            ),
        )
        assertThat(deleted.total()).isEqualTo(25)
        ROOM_CHILD_TABLES.keys.forEach { table ->
            assertThat(countRows(table, qaRoomId)).describedAs("$table 에 QA 룸 행이 남아 있다").isZero()
        }
        assertThat(roomRepository.existsById(qaRoomId)).isFalse()

        assertThat(roomRepository.existsById(liveRoomId)).isTrue()
        ROOM_CHILD_TABLES.keys.forEach { table ->
            assertThat(countRows(table, liveRoomId)).describedAs("$table 의 다른 룸 행이 지워졌다").isPositive()
        }
        assertThat(memberRepository.existsById(hostId)).isTrue()
        assertThat(memberRepository.existsById(guestId)).isTrue()
    }

    @Test
    fun `제목이 QA 마커로 시작하지 않는 룸은 E2201 로 거절하고 아무것도 지우지 않는다`() {
        seedMembers()
        seedRoom(liveRoomId, "면접 스터디 (실데이터)")
        seedGraph(liveRoomId)

        assertThatThrownBy { qaRoomEraser.erase(liveRoomId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.QA_DATA_ONLY)
            }

        assertThat(roomRepository.existsById(liveRoomId)).isTrue()
        assertThat(countRows("participation", liveRoomId)).isEqualTo(2)
    }

    @Test
    fun `없는 룸은 E1405 를 던진다`() {
        assertThatThrownBy { qaRoomEraser.erase(UUID.randomUUID()) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.ROOM_NOT_FOUND)
            }
    }

    @Test
    fun `일괄 삭제는 접두가 맞는 룸만 지우고 hostMemberId 로 좁힐 수 있다`() {
        seedMembers()
        seedRoom(qaRoomId, "[QA] smoke-1", hostMemberId = hostId)
        seedRoom(otherQaRoomId, "[QA] smoke-2", hostMemberId = guestId)
        seedRoom(liveRoomId, "마커 없는 실데이터 룸", hostMemberId = hostId)

        val byHost = eraseAll(QaDataCondition(prefix = "[QA] smoke-", hostMemberId = hostId))

        assertThat(byHost.rooms).isEqualTo(1)
        assertThat(roomRepository.existsById(qaRoomId)).isFalse()
        assertThat(roomRepository.existsById(otherQaRoomId)).isTrue()

        val remaining = eraseAll(QaDataCondition(prefix = "[QA] smoke-", hostMemberId = null))

        assertThat(remaining.rooms).isEqualTo(1)
        assertThat(roomRepository.existsById(otherQaRoomId)).isFalse()
        assertThat(roomRepository.existsById(liveRoomId)).isTrue()
    }

    @Test
    fun `목록은 접두가 맞는 룸의 방장과 행 수를 보여준다`() {
        seedMembers()
        seedRoom(qaRoomId, "[QA] 목록 룸")
        seedGraph(qaRoomId)
        seedRoom(liveRoomId, "마커 없는 실데이터 룸")

        val rooms = qaRoomFinder.getRooms(QaDataCondition(prefix = "[QA] 목록", hostMemberId = null))

        assertThat(rooms).hasSize(1)
        with(rooms.single()) {
            assertThat(id).isEqualTo(qaRoomId)
            assertThat(hostMemberId).isEqualTo(hostId)
            assertThat(status).isEqualTo(RoomStatus.RECRUITING)
            assertThat(applicationCount).isEqualTo(1)
            assertThat(participantCount).isEqualTo(2)
        }
    }

    @Test
    fun `접두의 LIKE 와일드카드는 문자 그대로 비교한다`() {
        seedMembers()
        seedRoom(qaRoomId, "[QA] 100% 룸")
        seedRoom(otherQaRoomId, "[QA] 100 룸")

        val rooms = qaRoomFinder.getRooms(QaDataCondition(prefix = "[QA] 100%", hostMemberId = null))

        assertThat(rooms.map { it.id }).containsExactly(qaRoomId)
    }

    private fun eraseAll(condition: QaDataCondition): QaDeletedRows = qaRoomFinder.getRoomIds(condition).fold(QaDeletedRows.NONE) { acc, id -> acc + qaRoomEraser.erase(id) }

    private fun seedMembers() {
        listOf(hostId to "host", guestId to "guest").forEach { (id, label) ->
            val suffix = id.toString().take(8)
            memberRepository.saveAndFlush(
                MemberEntity(
                    id = id,
                    email = "qa-$label-$suffix@example.com",
                    nickname = "qa$label$suffix",
                    status = MemberStatus.ACTIVE,
                    lastLoginAt = at,
                ),
            )
        }
    }

    private fun seedRoom(roomId: UUID, title: String, hostMemberId: UUID = hostId) {
        roomRepository.saveAndFlush(
            RoomEntity(
                id = roomId,
                jobPostingId = 1L,
                jobRoleId = 1L,
                sigunguId = null,
                title = title,
                description = null,
                interviewStage = InterviewStage.FIRST,
                interviewType = InterviewType.JOB,
                meetingType = MeetingType.ONLINE,
                minCapacity = 2,
                maxCapacity = 6,
                startAt = at.plusDays(7),
                durationMinutes = 60,
            ),
        )
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = hostMemberId,
                participationRole = ParticipationRole.HOST,
                status = ParticipationStatus.JOINED,
                joinedAt = at,
            ),
        )
    }

    private fun seedGraph(roomId: UUID) {
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = guestId,
                participationRole = ParticipationRole.PARTICIPANT,
                status = ParticipationStatus.JOINED,
                joinedAt = at,
            ),
        )
        val application = roomApplicationRepository.saveAndFlush(
            RoomApplicationEntity(
                roomId = roomId,
                applicantMemberId = guestId,
                note = "참여하고 싶습니다",
                appliedAt = at,
                status = RoomApplicationStatus.ACCEPTED,
                pendingMemberId = null,
            ),
        )
        resumeSubmissionRepository.saveAndFlush(
            ResumeSubmissionEntity(
                roomApplicationId = application.id,
                roomId = roomId,
                memberId = guestId,
                sourceResumeId = UUID.randomUUID(),
                fileKey = "resumes/$roomId.pdf",
                originalName = "resume.pdf",
                sizeBytes = 1024,
                contentType = "application/pdf",
                submittedAt = at,
            ),
        )
        roomStatusLogRepository.saveAndFlush(RoomStatusLogEntity.byMember(roomId, RoomStatus.CONFIRMED, hostId, at))
        attendanceRepository.saveAll(
            listOf(hostId, guestId).map {
                AttendanceEntity(roomId = roomId, memberId = it, status = AttendanceStatus.ATTENDED, recorderMemberId = hostId, recordedAt = at)
            },
        )
        val question = questionRepository.saveAndFlush(
            QuestionEntity(roomId = roomId, targetMemberId = guestId, authorMemberId = hostId, content = "자기소개", source = QuestionSource.PREPARATION),
        )
        questionRepository.saveAndFlush(
            QuestionEntity(
                roomId = roomId,
                targetMemberId = guestId,
                authorMemberId = hostId,
                parentQuestionId = question.id,
                content = "꼬리질문",
                source = QuestionSource.IN_PROGRESS,
            ),
        )
        answerSummaryRepository.saveAndFlush(AnswerSummaryEntity(questionId = question.id, authorMemberId = guestId, content = "요약"))
        questionCommentRepository.saveAndFlush(
            QuestionCommentEntity(questionId = question.id, authorMemberId = hostId, commentType = QuestionCommentType.MEMO, content = "메모"),
        )
        closingResponseRepository.saveAndFlush(
            ClosingResponseEntity(
                roomId = roomId,
                memberId = guestId,
                questionVotes = listOf(QuestionVoteEntity(questionId = question.id, vote = QuestionVote.MEMORABLE)),
            ),
        )
        roundFeedbackRepository.saveAndFlush(
            RoundFeedbackEntity(
                roomId = roomId,
                intervieweeMemberId = guestId,
                authorMemberId = hostId,
                feedbackType = RoundFeedbackType.FINAL,
                content = "피드백",
            ),
        )
        reviewRepository.saveAndFlush(
            ReviewEntity(
                roomId = roomId,
                authorMemberId = hostId,
                targetMemberId = guestId,
                content = "좋았어요",
                anonymous = false,
                visibleAt = at,
                tags = listOf("KIND", "SHARP"),
            ),
        )
        reviewSkipRepository.saveAndFlush(ReviewSkipEntity(roomId = roomId, authorMemberId = guestId, targetMemberId = hostId))
        val guestbook = roomGuestbookRepository.saveAndFlush(RoomGuestbookEntity(roomId = roomId))
        guestbookPostRepository.saveAll(
            listOf("첫 댓글", "둘째 댓글").map { GuestbookPostEntity(roomGuestbookId = guestbook.id, authorMemberId = hostId, content = it) },
        )
        seedInterviewPlan(roomId)
    }

    private fun seedInterviewPlan(roomId: UUID) {
        jdbcTemplate.update(
            "insert into interview_plan (room_id, opening_minutes, closing_minutes, created_at, updated_at) values (?, 5, 5, ?, ?)",
            bytes(roomId),
            at,
            at,
        )
        val planId = jdbcTemplate.queryForObject("select id from interview_plan where room_id = ?", Long::class.javaObjectType, bytes(roomId))
        jdbcTemplate.update(
            "insert into interview_round (interview_plan_id, seq, interviewee_member_id, interview_minutes, feedback_minutes, created_at, updated_at) values (?, 1, ?, 10, 5, ?, ?)",
            planId,
            bytes(guestId),
            at,
            at,
        )
        val roundId = jdbcTemplate.queryForObject("select id from interview_round where interview_plan_id = ?", Long::class.javaObjectType, planId)
        jdbcTemplate.update(
            "insert into round_assignment (interview_round_id, member_id, assignment_role, created_at, updated_at) values (?, ?, 'INTERVIEWER', ?, ?)",
            roundId,
            bytes(hostId),
            at,
            at,
        )
    }

    private fun countRows(table: String, roomId: UUID): Long = jdbcTemplate.queryForObject(
        "select count(*) from $table where ${ROOM_CHILD_TABLES.getValue(table).substringAfter("where ")}",
        Long::class.javaObjectType,
        *bindings(table, roomId),
    )!!

    private fun bindings(table: String, roomId: UUID): Array<Any> = Array(ROOM_CHILD_TABLES.getValue(table).count { it == '?' }) { bytes(roomId) }

    private fun bytes(uuid: UUID): ByteArray = ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array()

    companion object {
        private val ROOM_CHILD_TABLES: Map<String, String> = linkedMapOf(
            "guestbook_post" to "delete from guestbook_post where room_guestbook_id in (select id from room_guestbook where room_id = ?)",
            "room_guestbook" to "delete from room_guestbook where room_id = ?",
            "review_tag" to "delete from review_tag where review_id in (select id from review where room_id = ?)",
            "review" to "delete from review where room_id = ?",
            "review_skip" to "delete from review_skip where room_id = ?",
            "attendance" to "delete from attendance where room_id = ?",
            "question_vote" to "delete from question_vote where question_id in (select id from question where room_id = ?)",
            "closing_response" to "delete from closing_response where room_id = ?",
            "question_comment" to "delete from question_comment where question_id in (select id from question where room_id = ?)",
            "answer_summary" to "delete from answer_summary where question_id in (select id from question where room_id = ?)",
            "question" to "delete from question where room_id = ?",
            "round_feedback" to "delete from round_feedback where room_id = ?",
            "round_assignment" to "delete from round_assignment where interview_round_id in (select ir.id from interview_round ir join interview_plan ip on ip.id = ir.interview_plan_id where ip.room_id = ?)",
            "interview_round" to "delete from interview_round where interview_plan_id in (select id from interview_plan where room_id = ?)",
            "interview_plan" to "delete from interview_plan where room_id = ?",
            "resume_submission" to "delete from resume_submission where room_id = ?",
            "participation" to "delete from participation where room_id = ?",
            "room_application" to "delete from room_application where room_id = ?",
            "room_status_log" to "delete from room_status_log where room_id = ?",
        )
    }
}
