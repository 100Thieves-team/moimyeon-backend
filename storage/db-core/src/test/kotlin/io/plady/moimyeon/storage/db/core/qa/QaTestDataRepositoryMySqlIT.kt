package io.plady.moimyeon.storage.db.core.qa

import io.plady.moimyeon.core.enums.InterviewStage
import io.plady.moimyeon.core.enums.InterviewType
import io.plady.moimyeon.core.enums.MeetingType
import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.storage.db.CoreDbTestApplication
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.ReviewEntity
import io.plady.moimyeon.storage.db.core.ReviewRepository
import io.plady.moimyeon.storage.db.core.RoomEntity
import io.plady.moimyeon.storage.db.core.RoomRepository
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
    fun `hostMemberId 필터는 현재 방장의 룸만 남긴다`() {
        val host = UUID.randomUUID()
        val other = UUID.randomUUID()
        val hosted = seedRoom("[QA] 내 룸", hostMemberId = host)
        seedRoom("[QA] 남의 룸", hostMemberId = other)

        assertThat(qaTestDataRepository.findRoomsByTitlePrefix("[QA]", host).map { it.id }).containsExactly(hosted)
        assertThat(qaTestDataRepository.findHostedRoomIds(host)).containsExactly(hosted)
        assertThat(qaTestDataRepository.findHostMemberId(hosted)).isEqualTo(host)
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
