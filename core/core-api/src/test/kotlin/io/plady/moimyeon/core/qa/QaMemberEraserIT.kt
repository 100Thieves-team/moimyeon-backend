package io.plady.moimyeon.core.qa

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.AttendanceStatus
import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.QuestionSource
import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.AttendanceEntity
import io.plady.moimyeon.storage.db.core.AttendanceRepository
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.QuestionEntity
import io.plady.moimyeon.storage.db.core.QuestionRepository
import io.plady.moimyeon.storage.db.core.ResumeEntity
import io.plady.moimyeon.storage.db.core.ResumeRepository
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.util.UUID

class QaMemberEraserIT(
    private val qaMemberEraser: QaMemberEraser,
    private val qaMemberCreator: QaMemberCreator,
    private val qaMemberFinder: QaMemberFinder,
    private val jdbcTemplate: JdbcTemplate,
    private val memberRepository: MemberRepository,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val resumeRepository: ResumeRepository,
    private val attendanceRepository: AttendanceRepository,
    private val questionRepository: QuestionRepository,
    private val reviewRepository: ReviewRepository,
) : ContextTest() {
    private val at = LocalDateTime.of(2026, 9, 22, 10, 0)
    private val memberIds = mutableListOf<UUID>()
    private val roomIds = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        roomIds.forEach { roomId ->
            listOf(
                "delete from review_tag where review_id in (select id from review where room_id = ?)",
                "delete from review where room_id = ?",
                "delete from question where room_id = ?",
                "delete from attendance where room_id = ?",
                "delete from participation where room_id = ?",
                "delete from room_application where room_id = ?",
                "delete from room where id = ?",
            ).forEach { jdbcTemplate.update(it, bytes(roomId)) }
        }
        memberIds.forEach { id ->
            listOf(
                "delete from resume where member_id = ?",
                "delete from terms_agreement where member_id = ?",
                "delete from member_profile where member_id = ?",
                "delete from social_account where member_id = ?",
                "delete from member where id = ?",
            ).forEach { jdbcTemplate.update(it, bytes(id)) }
        }
    }

    @Test
    fun `QA 생성 회원을 남긴 행과 소유 행까지 지우고 다른 회원은 손대지 않는다`() {
        val other = qaMemberCreator.create().id.also { memberIds += it }
        val tester = qaMemberCreator.create().id.also { memberIds += it }
        val hosted = seedRoom("[QA] 내 룸", host = tester)
        val others = seedRoom("[QA] 남의 룸", host = other)
        val live = seedRoom("실데이터 룸", host = other)
        join(others, tester)
        join(live, tester)
        attendanceRepository.saveAndFlush(AttendanceEntity(live, tester, AttendanceStatus.ATTENDED, recorderMemberId = other, recordedAt = at))
        questionRepository.saveAndFlush(QuestionEntity(live, targetMemberId = tester, authorMemberId = other, content = "질문", source = QuestionSource.PREPARATION))
        reviewRepository.saveAndFlush(ReviewEntity(live, authorMemberId = tester, targetMemberId = other, anonymous = false, visibleAt = at, tags = listOf("KIND")))
        attendanceRepository.saveAndFlush(AttendanceEntity(others, tester, AttendanceStatus.ATTENDED, recorderMemberId = other, recordedAt = at))
        questionRepository.saveAndFlush(QuestionEntity(others, targetMemberId = tester, authorMemberId = other, content = "질문", source = QuestionSource.PREPARATION))
        val asked = questionRepository.saveAndFlush(QuestionEntity(others, targetMemberId = other, authorMemberId = tester, content = "질문", source = QuestionSource.PREPARATION))
        questionRepository.saveAndFlush(
            QuestionEntity(others, targetMemberId = other, authorMemberId = other, parentQuestionId = asked.id, content = "남이 단 꼬리질문", source = QuestionSource.IN_PROGRESS),
        )
        reviewRepository.saveAndFlush(ReviewEntity(others, authorMemberId = other, targetMemberId = tester, anonymous = false, visibleAt = at, tags = listOf("KIND")))
        seedResume(tester)

        val deleted = qaMemberEraser.erase(tester)

        assertThat(deleted.members).isEqualTo(1)
        assertThat(deleted.rooms).isEqualTo(1)
        assertThat(deleted.participants).isEqualTo(3)
        assertThat(deleted.attendances).isEqualTo(2)
        assertThat(deleted.questions).isEqualTo(4)
        assertThat(questionRepository.findAll().filter { it.roomId == others || it.roomId == live }).isEmpty()
        assertThat(deleted.reviews).isEqualTo(2)
        assertThat(deleted.reviewTags).isEqualTo(2)
        assertThat(deleted.resumes).isEqualTo(1)
        assertThat(deleted.profiles).isEqualTo(1)
        assertThat(deleted.socialAccounts).isEqualTo(1)
        assertThat(deleted.termsAgreements).isPositive()

        assertThat(memberRepository.existsById(tester)).isFalse()
        assertThat(countByMember("social_account", tester)).isZero()
        assertThat(countByMember("member_profile", tester)).isZero()
        assertThat(countByMember("terms_agreement", tester)).isZero()
        assertThat(roomRepository.existsById(hosted)).isFalse()
        assertThat(roomRepository.existsById(others)).isTrue()
        assertThat(roomRepository.existsById(live)).isTrue()
        assertThat(memberRepository.existsById(other)).isTrue()
        assertThat(participationRepository.findAll().filter { it.roomId == others || it.roomId == live }).hasSize(2)
        assertThat(attendanceRepository.findAll().filter { it.roomId == live }).isEmpty()
        assertThat(qaMemberFinder.getQaMembers().map { it.id }).contains(other).doesNotContain(tester)
    }

    @Test
    fun `이메일 도메인과 소셜 식별자 접두를 둘 다 만족하지 않으면 E2201 로 거절하고 아무것도 지우지 않는다`() {
        val real = seedMember(email = "real@example.com", providerId = "1234567890")
        val emailOnly = seedMember(email = "qa-only-email@${QaMemberCreator.EMAIL_DOMAIN}", providerId = "1234567891")
        val providerOnly = seedMember(email = "provider-only@example.com", providerId = "${QaMemberCreator.PROVIDER_ID_PREFIX}x")

        listOf(real, emailOnly, providerOnly).forEach { id ->
            assertThatThrownBy { qaMemberEraser.erase(id) }
                .isInstanceOfSatisfying(CoreException::class.java) {
                    assertThat(it.errorType).isEqualTo(CoreErrorType.QA_DATA_ONLY)
                }
            assertThat(memberRepository.existsById(id)).isTrue()
        }
        assertThat(qaMemberFinder.getQaMembers().map { it.id }).doesNotContain(real, emailOnly, providerOnly)
    }

    @Test
    fun `일괄 삭제는 QA 생성 회원만 지우고 실제 회원은 남긴다`() {
        val first = qaMemberCreator.create().id.also { memberIds += it }
        val second = qaMemberCreator.create().id.also { memberIds += it }
        val real = seedMember(email = "real@example.com", providerId = "1234567892")

        val deleted = qaMemberFinder.getQaMembers().fold(QaDeletedRows.NONE) { acc, member -> acc + qaMemberEraser.erase(member.id) }

        assertThat(deleted.members).isGreaterThanOrEqualTo(2)
        assertThat(memberRepository.existsById(first)).isFalse()
        assertThat(memberRepository.existsById(second)).isFalse()
        assertThat(memberRepository.existsById(real)).isTrue()
        assertThat(qaMemberFinder.getQaMembers()).isEmpty()
    }

    @Test
    fun `탈퇴한 QA 생성 회원도 지운다`() {
        val tester = qaMemberCreator.create().id.also { memberIds += it }
        memberRepository.findById(tester).orElseThrow().let {
            it.delete(at)
            memberRepository.saveAndFlush(it)
        }

        val deleted = qaMemberEraser.erase(tester)

        assertThat(deleted.members).isEqualTo(1)
        assertThat(memberRepository.existsById(tester)).isFalse()
    }

    @Test
    fun `QA 생성 회원이라도 방장인 비QA 룸이 있으면 E2201 로 전체 거절한다`() {
        val tester = qaMemberCreator.create().id.also { memberIds += it }
        seedRoom("실데이터 룸", host = tester)

        assertThatThrownBy { qaMemberEraser.erase(tester) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.QA_DATA_ONLY)
            }

        assertThat(memberRepository.existsById(tester)).isTrue()
    }

    @Test
    fun `없는 회원은 E1006 을 던진다`() {
        assertThatThrownBy { qaMemberEraser.erase(UUID.randomUUID()) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND)
            }
    }

    private fun seedMember(email: String, providerId: String): UUID {
        val id = UUID.randomUUID().also { memberIds += it }
        memberRepository.saveAndFlush(
            MemberEntity(
                id = id,
                email = email,
                nickname = "m${id.toString().take(8)}",
                status = MemberStatus.ACTIVE,
                lastLoginAt = at,
                socialAccounts = listOf(SocialAccountEntity(provider = SocialLoginProvider.GOOGLE, providerId = providerId, linkedEmail = email)),
            ),
        )
        return id
    }

    private fun seedRoom(title: String, host: UUID): UUID {
        val roomId = UUID.randomUUID().also { roomIds += it }
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
            ParticipationEntity(roomId, host, ParticipationRole.HOST, ParticipationStatus.JOINED, joinedAt = at),
        )
        return roomId
    }

    private fun join(roomId: UUID, memberId: UUID) {
        participationRepository.saveAndFlush(
            ParticipationEntity(roomId, memberId, ParticipationRole.PARTICIPANT, ParticipationStatus.JOINED, joinedAt = at),
        )
    }

    private fun seedResume(memberId: UUID) {
        val id = UUID.randomUUID()
        resumeRepository.saveAndFlush(
            ResumeEntity(
                id = id,
                memberId = memberId,
                name = "[QA] resume.pdf",
                fileKey = "resumes/$id.pdf",
                originalName = "resume.pdf",
                sizeBytes = 1024,
                contentType = "application/pdf",
                summaryStatus = ResumeSummaryStatus.DONE,
                summaryContent = "요약",
                summaryStartedAt = at,
                isDefault = true,
            ),
        )
    }

    private fun countByMember(table: String, memberId: UUID): Long = jdbcTemplate.queryForObject("select count(*) from $table where member_id = ?", Long::class.javaObjectType, bytes(memberId))!!

    private fun bytes(uuid: UUID): ByteArray = ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array()
}
