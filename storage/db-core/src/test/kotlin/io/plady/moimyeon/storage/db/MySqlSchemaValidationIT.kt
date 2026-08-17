package io.plady.moimyeon.storage.db

import io.plady.moimyeon.core.enums.ParticipationRole
import io.plady.moimyeon.core.enums.ParticipationStatus
import io.plady.moimyeon.storage.db.core.ParticipationEntity
import io.plady.moimyeon.storage.db.core.ParticipationRepository
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
    private val participationRepository: ParticipationRepository,
    private val entityManager: EntityManager,
) {
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
        private const val PASSWORD = "moimyeon"

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
