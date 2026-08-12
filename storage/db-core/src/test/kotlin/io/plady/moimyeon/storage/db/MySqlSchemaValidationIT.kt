package io.plady.moimyeon.storage.db

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestConstructor
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
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
) {
    @Test
    fun `Flyway로 만든 MySQL TEXT 스키마와 JPA 매핑이 일치한다`() {
        assertThat(dataTypeOf("outbox", "payload")).isEqualTo("text")
        assertThat(dataTypeOf("web_push_subscription", "registration")).isEqualTo("text")
    }

    private fun dataTypeOf(
        tableName: String,
        columnName: String,
    ): String = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT DATA_TYPE
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
                resultSet.getString("DATA_TYPE")
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
