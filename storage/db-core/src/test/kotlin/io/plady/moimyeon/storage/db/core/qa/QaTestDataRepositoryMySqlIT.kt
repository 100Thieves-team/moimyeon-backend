package io.plady.moimyeon.storage.db.core.qa

import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.CoreDbTestApplication
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestConstructor
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.LocalDateTime
import java.util.UUID

@ActiveProfiles("test")
@Tag("context")
@Testcontainers
@SpringBootTest(
    classes = [CoreDbTestApplication::class],
    properties = [
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
    ],
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class QaTestDataRepositoryMySqlIT(
    private val qaTestDataRepository: QaTestDataRepository,
    private val roomRepository: RoomRepository,
    private val participationRepository: ParticipationRepository,
    private val reviewRepository: ReviewRepository,
    private val memberRepository: MemberRepository,
    private val entityManager: EntityManager,
) {
    private val at = LocalDateTime.of(2026, 9, 22, 10, 0)

    @Test
    @Transactional
    fun `MySQL 의 제목 LIKE 는 대소문자를 무시한다`() {
        val upper = seedRoom("[QA] 대문자 마커")
        val lower = seedRoom("[qa] 소문자 마커")
        seedRoom("마커 없는 룸")

        val found = qaTestDataRepository.findRoomsByTitlePrefix("[QA]", null).map { it.id }

        assertThat(found).containsExactlyInAnyOrder(upper, lower)
    }

    @Test
    @Transactional
    fun `LIKE 와일드카드는 이스케이프되어 문자 그대로 비교된다`() {
        val percent = seedRoom("[QA] 100% 룸")
        seedRoom("[QA] 100 룸")
        val underscore = seedRoom("[QA] a_b")
        seedRoom("[QA] axb")

        assertThat(qaTestDataRepository.findRoomsByTitlePrefix("[QA] 100%", null).map { it.id }).containsExactly(percent)
        assertThat(qaTestDataRepository.findRoomsByTitlePrefix("[QA] a_b", null).map { it.id }).containsExactly(underscore)
    }

    @Test
    @Transactional
    fun `QA 회원 판정은 이메일 도메인과 소셜 식별자 접두를 둘 다 만족할 때만 참이다`() {
        val qa = seedMember("qa-1@qa.moimyeon.test", "qa-1")
        val emailOnly = seedMember("qa-2@qa.moimyeon.test", "1234567890")
        val providerOnly = seedMember("qa-3@example.com", "qa-3")
        val upper = seedMember("QA-4@QA.MOIMYEON.TEST", "QA-4")

        val found = qaTestDataRepository.findQaMembers("qa-", "qa.moimyeon.test").map { it.id }

        assertThat(found).contains(qa, upper).doesNotContain(emailOnly, providerOnly)
        assertThat(qaTestDataRepository.isQaMember(qa, "qa-", "qa.moimyeon.test")).isTrue()
        assertThat(qaTestDataRepository.isQaMember(emailOnly, "qa-", "qa.moimyeon.test")).isFalse()
        assertThat(qaTestDataRepository.isQaMember(providerOnly, "qa-", "qa.moimyeon.test")).isFalse()
    }

    @Test
    @Transactional
    fun `회원 기준 native 삭제는 UUID 바인딩으로 소셜 계정·관심 정보·클로징 평가를 지운다`() {
        val memberId = seedMember("qa-native@qa.moimyeon.test", "qa-native")
        val profileId = UUID.randomUUID()
        entityManager.createNativeQuery(
            "insert into member_profile (id, member_id, bio, created_at, updated_at) values (:id, :memberId, '', :at, :at)",
        ).setParameter("id", profileId).setParameter("memberId", memberId).setParameter("at", at).executeUpdate()
        entityManager.createNativeQuery(
            "insert into member_profile_interest_company (profile_id, company_id, created_at, updated_at) values (:profileId, 1, :at, :at)",
        ).setParameter("profileId", profileId).setParameter("at", at).executeUpdate()
        val roomId = seedRoom("[QA] 클로징 룸", hostMemberId = memberId)
        entityManager.createNativeQuery(
            "insert into question (room_id, target_member_id, author_member_id, content, source, asked, created_at, updated_at) values (:roomId, :memberId, :memberId, 'q', 'PREPARATION', false, :at, :at)",
        ).setParameter("roomId", roomId).setParameter("memberId", memberId).setParameter("at", at).executeUpdate()
        val questionId = (entityManager.createNativeQuery("select id from question where room_id = :roomId").setParameter("roomId", roomId).singleResult as Number).toLong()
        entityManager.createNativeQuery(
            "insert into closing_response (room_id, member_id, created_at, updated_at) values (:roomId, :memberId, :at, :at)",
        ).setParameter("roomId", roomId).setParameter("memberId", memberId).setParameter("at", at).executeUpdate()
        val closingId = (entityManager.createNativeQuery("select id from closing_response where room_id = :roomId").setParameter("roomId", roomId).singleResult as Number).toLong()
        entityManager.createNativeQuery(
            "insert into question_vote (closing_response_id, question_id, vote, created_at, updated_at) values (:closingId, :questionId, 'MEMORABLE', :at, :at)",
        ).setParameter("closingId", closingId).setParameter("questionId", questionId).setParameter("at", at).executeUpdate()

        assertThat(qaTestDataRepository.deleteMemberClosingResponseVotes(memberId)).isEqualTo(1)
        assertThat(qaTestDataRepository.deleteMemberClosingResponses(memberId)).isEqualTo(1)
        assertThat(qaTestDataRepository.deleteMemberProfileInterests(memberId)).isEqualTo(1)
        assertThat(qaTestDataRepository.deleteMemberProfile(memberId)).isEqualTo(1)
        assertThat(qaTestDataRepository.deleteMemberSocialAccounts(memberId)).isEqualTo(1)
        assertThat(qaTestDataRepository.deleteMember(memberId)).isEqualTo(1)
    }

    @Test
    @Transactional
    fun `hostMemberId 필터는 현재 방장의 룸만 남긴다`() {
        val host = UUID.randomUUID()
        val other = UUID.randomUUID()
        val hosted = seedRoom("[QA] 내 룸", hostMemberId = host)
        seedRoom("[QA] 남의 룸", hostMemberId = other)

        assertThat(qaTestDataRepository.findRoomsByTitlePrefix("[QA]", host).map { it.id }).containsExactly(hosted)
        assertThat(qaTestDataRepository.findHostedRoomIds(host)).containsExactly(hosted)
        assertThat(qaTestDataRepository.findHostMemberIds(listOf(hosted))).containsEntry(hosted, host)
    }

    @Test
    @Transactional
    fun `native 쿼리의 UUID 바인딩으로 엔티티 없는 진행 테이블을 지운다`() {
        val roomId = seedRoom("[QA] 진행 룸")
        entityManager.createNativeQuery(
            "insert into interview_plan (room_id, opening_minutes, closing_minutes, created_at, updated_at) values (:roomId, 5, 5, :at, :at)",
        ).setParameter("roomId", roomId).setParameter("at", at).executeUpdate()
        val planId = (
            entityManager.createNativeQuery("select id from interview_plan where room_id = :roomId")
                .setParameter("roomId", roomId).singleResult as Number
            ).toLong()
        entityManager.createNativeQuery(
            "insert into interview_round (interview_plan_id, seq, interview_minutes, feedback_minutes, created_at, updated_at) values (:planId, 1, 10, 5, :at, :at)",
        ).setParameter("planId", planId).setParameter("at", at).executeUpdate()
        val roundId = (
            entityManager.createNativeQuery("select id from interview_round where interview_plan_id = :planId")
                .setParameter("planId", planId).singleResult as Number
            ).toLong()
        entityManager.createNativeQuery(
            "insert into round_assignment (interview_round_id, member_id, assignment_role, created_at, updated_at) values (:roundId, :memberId, 'INTERVIEWER', :at, :at)",
        ).setParameter("roundId", roundId).setParameter("memberId", UUID.randomUUID()).setParameter("at", at).executeUpdate()

        assertThat(qaTestDataRepository.deleteRoundAssignments(roomId)).isEqualTo(1)
        assertThat(qaTestDataRepository.deleteInterviewRounds(roomId)).isEqualTo(1)
        assertThat(qaTestDataRepository.deleteInterviewPlans(roomId)).isEqualTo(1)
    }

    @Test
    @Transactional
    fun `후기 태그는 review id 로 native 삭제한다`() {
        val roomId = seedRoom("[QA] 후기 룸")
        reviewRepository.saveAndFlush(
            ReviewEntity(
                roomId = roomId,
                authorMemberId = UUID.randomUUID(),
                targetMemberId = UUID.randomUUID(),
                content = "좋았어요",
                anonymous = false,
                visibleAt = at,
                tags = listOf("KIND", "SHARP"),
            ),
        )

        assertThat(qaTestDataRepository.deleteReviewTags(roomId)).isEqualTo(2)
        assertThat(qaTestDataRepository.deleteReviews(roomId)).isEqualTo(1)
    }

    private fun seedMember(email: String, providerId: String): UUID {
        val id = UUID.randomUUID()
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

    private fun seedRoom(title: String, hostMemberId: UUID = UUID.randomUUID()): UUID {
        val roomId = UUID.randomUUID()
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
        return roomId
    }

    companion object {
        private const val MYSQL_PORT = 3306
        private const val DATABASE_NAME = "core"
        private const val USERNAME = "moimyeon"
        private const val PASSWORD = "moimyeon" // gate:allow-secret (Testcontainers 로컬 자격증명)

        @Container
        @JvmStatic
        private val mysql =
            GenericContainer(DockerImageName.parse("mysql:8.4.9"))
                .withEnv("MYSQL_DATABASE", DATABASE_NAME)
                .withEnv("MYSQL_USER", USERNAME)
                .withEnv("MYSQL_PASSWORD", PASSWORD)
                .withEnv("MYSQL_ROOT_PASSWORD", "root")
                .withExposedPorts(MYSQL_PORT)

        @DynamicPropertySource
        @JvmStatic
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            val jdbcUrl = { "jdbc:mysql://${mysql.host}:${mysql.getMappedPort(MYSQL_PORT)}/$DATABASE_NAME" }
            registry.add("storage.datasource.core.driver-class-name") { "com.mysql.cj.jdbc.Driver" }
            registry.add("storage.datasource.core.jdbc-url", jdbcUrl)
            registry.add("storage.datasource.core.username") { USERNAME }
            registry.add("storage.datasource.core.password") { PASSWORD }
            registry.add("spring.flyway.url", jdbcUrl)
            registry.add("spring.flyway.user") { USERNAME }
            registry.add("spring.flyway.password") { PASSWORD }
        }
    }
}
