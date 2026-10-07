package io.plady.moimyeon.storage.db

import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.core.enums.RoomApplicationStatus
import io.plady.moimyeon.core.enums.TermsStatus
import io.plady.moimyeon.core.enums.TermsType
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
import io.plady.moimyeon.storage.db.core.RoomApplicationEntity
import io.plady.moimyeon.storage.db.core.RoomApplicationRepository
import io.plady.moimyeon.storage.db.core.TermsRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
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
import javax.sql.DataSource

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
class MySqlSchemaValidationIT(
    private val dataSource: DataSource,
    private val flyway: Flyway,
    private val participationRepository: ParticipationRepository,
    private val roomApplicationRepository: RoomApplicationRepository,
    private val entityManager: EntityManager,
    private val termsRepository: TermsRepository,
) {
    @Test
    fun `빈 MySQL에 모든 Flyway migration을 적용한다`() {
        assertThat(flyway.info().applied()).isNotEmpty()
        assertThat(flyway.info().pending()).isEmpty()
    }

    @Test
    fun `Flyway 약관 발행은 긴 한글 본문과 기존 공개 버전을 함께 보존한다`() {
        val active = termsRepository.findByStatusAndDeletedAtIsNull(TermsStatus.ACTIVE)
        val published = active.filter { it.version == "v1.1" }
        assertThat(published.map { it.type }).containsExactlyInAnyOrder(TermsType.SERVICE, TermsType.PRIVACY)
        published.forEach { terms ->
            assertThat(terms.effectiveFrom).isEqualTo(LocalDateTime.of(2026, 10, 7, 0, 0))
            assertThat(terms.content).contains("이유제", "010-9328-9628", "\n## ", "2026년 10월 7일")
            assertThat(terms.content.toByteArray(Charsets.UTF_8).size).isBetween(8_000, 65_535)
        }
        assertThat(active.map { it.version }).containsExactlyInAnyOrder("v1.0", "v1.0", "v1.1", "v1.1")
        assertThat(termsRepository.findByStatusAndDeletedAtIsNull(TermsStatus.DRAFT)).isEmpty()
    }

    @Test
    @Transactional
    fun `확정 시점 참여 여부를 MySQL에서 조회한다`() {
        val roomId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        val confirmedAt = LocalDateTime.of(2026, 8, 15, 12, 0)
        participationRepository.saveAndFlush(
            ParticipationEntity(
                roomId = roomId,
                memberId = memberId,
                participationRole = ParticipationRole.PARTICIPANT,
                status = ParticipationStatus.JOINED,
                joinedAt = confirmedAt.minusDays(1),
            ),
        )
        entityManager.createNativeQuery(
            """
            insert into room_status_log (
                room_id, transition_type, handler_type, handler_member_id, occurred_at,
                created_at, updated_at, deleted_at
            ) values (
                :roomId, 'CONFIRMED', 'MEMBER', :handlerMemberId, :confirmedAt,
                :confirmedAt, :confirmedAt, null
            )
            """.trimIndent(),
        )
            .setParameter("roomId", roomId)
            .setParameter("handlerMemberId", UUID.randomUUID())
            .setParameter("confirmedAt", confirmedAt)
            .executeUpdate()

        assertThat(participationRepository.countAtRoomConfirmation(roomId, memberId)).isEqualTo(1)
    }

    // DATETIME(6) 저장과 조회 조건의 소수점 처리가 같아야 한다. H2 는 이 차이를 재현하지 못한다.
    @Test
    @Transactional
    fun `일괄 종료에 쓴 시각이 나노초여도 MySQL에서 닫힌 신청자를 그대로 되짚는다`() {
        val roomId = UUID.randomUUID()
        val applicantId = UUID.randomUUID()
        val closedAt = LocalDateTime.of(2026, 9, 24, 12, 0, 0, 123_456_789)
        roomApplicationRepository.saveAndFlush(
            RoomApplicationEntity(
                roomId = roomId,
                applicantMemberId = applicantId,
                note = "참여하고 싶습니다",
                appliedAt = closedAt.minusDays(1),
                status = RoomApplicationStatus.PENDING,
                pendingMemberId = applicantId,
            ),
        )

        roomApplicationRepository.closeAllPending(roomId, RoomApplicationStatus.ROOM_CANCELED, closedAt)

        assertThat(
            roomApplicationRepository.findApplicantMemberIdsClosedAt(roomId, RoomApplicationStatus.ROOM_CANCELED, closedAt),
        ).containsExactly(applicantId)
    }

    @Test
    fun `Flyway로 만든 MySQL TEXT 스키마와 JPA 매핑이 일치한다`() {
        assertThat(dataTypeOf("outbox", "payload")).isEqualTo("text")
        assertThat(dataTypeOf("web_push_subscription", "registration")).isEqualTo("text")
    }

    // 컬럼 목록을 손으로 적지 않는다. 초 단위로 들어온 새 테이블도 여기서 걸려야 한다(MOI-428).
    @Test
    fun `Flyway로 만든 MySQL 스키마의 시각 컬럼이 전부 마이크로초 정밀도다`() {
        assertThat(secondPrecisionColumns()).isEmpty()
    }

    // MODIFY COLUMN 은 컬럼 정의를 통째로 갈아치운다. 크롤러 적재 파이프라인이 기대는 자동 갱신이
    // 정밀도 변경에 딸려 사라지면 앱은 멀쩡한데 크롤러 쪽 갱신 시각만 조용히 멈춘다(MOI-428).
    @Test
    fun `크롤러 테이블의 updated_at 은 자동 갱신을 유지한다`() {
        listOf("sido", "sigungu", "job_group", "job_role", "company", "job_posting").forEach { table ->
            assertThat(columnOf(table, "updated_at", "EXTRA"))
                .describedAs("$table.updated_at")
                .contains("on update CURRENT_TIMESTAMP(6)")
        }
    }

    @Test
    fun `회원 프로필 스키마에서 진행 방식 선호와 선호 지역을 제거한다`() {
        assertThat(columnNamesOf("member_profile"))
            .doesNotContain("meeting_preference", "sigungu_id")
    }

    @Test
    fun `질문 평가는 클로징 제출의 소유 키만 가진다`() {
        assertThat(columnNamesOf("question_vote"))
            .contains("closing_response_id")
            .doesNotContain("voter_member_id", "deleted_at", "_active_check")
    }

    @Test
    fun `레거시 후기 컬럼은 rolling deployment 호환 상태로 유지한다`() {
        assertThat(columnNamesOf("review"))
            .contains("rating", "meet_again")
        assertThat(columnOf("review", "rating", "COLUMN_DEFAULT")).isEqualTo("0")
    }

    @Test
    fun `기존 후기는 익명으로 보존하고 새 후기의 익명 여부를 저장한다`() {
        assertThat(columnNamesOf("review")).contains("anonymous")
        assertThat(columnOf("review", "anonymous", "IS_NULLABLE")).isEqualTo("NO")
        assertThat(columnOf("review", "anonymous", "COLUMN_DEFAULT")).isEqualTo("1")
    }

    @Test
    fun `기존 회원의 알림 수신 설정은 컬럼 기본값으로 채워진다`() {
        assertThat(columnOf("member", "is_web_push_allowed", "IS_NULLABLE")).isEqualTo("NO")
        assertThat(columnOf("member", "is_web_push_allowed", "COLUMN_DEFAULT")).isEqualTo("1")
        assertThat(columnOf("member", "is_activity_email_enabled", "IS_NULLABLE")).isEqualTo("NO")
        assertThat(columnOf("member", "is_activity_email_enabled", "COLUMN_DEFAULT")).isEqualTo("1")
        assertThat(columnOf("member", "is_marketing_email_agreed", "IS_NULLABLE")).isEqualTo("NO")
        assertThat(columnOf("member", "is_marketing_email_agreed", "COLUMN_DEFAULT")).isEqualTo("0")
        assertThat(columnOf("member", "marketing_email_agreed_at", "IS_NULLABLE")).isEqualTo("YES")
    }

    @Test
    fun `후기 건너뛰기는 수정되지 않는 대상별 기록으로 저장한다`() {
        assertThat(columnNamesOf("review_skip")).containsExactly(
            "id",
            "room_id",
            "author_member_id",
            "target_member_id",
            "created_at",
        )
    }

    private fun secondPrecisionColumns(): List<String> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME) AS column_path
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = ?
              AND DATA_TYPE = 'datetime'
              AND DATETIME_PRECISION <> 6
            ORDER BY TABLE_NAME, COLUMN_NAME
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, DATABASE_NAME)
            statement.executeQuery().use { resultSet ->
                generateSequence { if (resultSet.next()) resultSet.getString("column_path") else null }.toList()
            }
        }
    }

    private fun dataTypeOf(
        tableName: String,
        columnName: String,
    ): String = columnOf(tableName, columnName, "DATA_TYPE")

    private fun columnNamesOf(tableName: String): List<String> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT COLUMN_NAME
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = ?
              AND TABLE_NAME = ?
            ORDER BY ORDINAL_POSITION
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, DATABASE_NAME)
            statement.setString(2, tableName)
            statement.executeQuery().use { resultSet ->
                generateSequence { if (resultSet.next()) resultSet.getString("COLUMN_NAME") else null }.toList()
            }
        }
    }

    // attribute 는 information_schema.COLUMNS 의 컬럼명이다. 테스트 안의 리터럴만 넘긴다.
    private fun columnOf(
        tableName: String,
        columnName: String,
        attribute: String,
    ): String = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT $attribute
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = ?
              AND TABLE_NAME = ?
              AND COLUMN_NAME = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, DATABASE_NAME)
            statement.setString(2, tableName)
            statement.setString(3, columnName)
            statement.executeQuery().use { resultSet ->
                check(resultSet.next()) { "$tableName.$columnName 컬럼을 찾을 수 없습니다." }
                resultSet.getString(attribute)
            }
        }
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
