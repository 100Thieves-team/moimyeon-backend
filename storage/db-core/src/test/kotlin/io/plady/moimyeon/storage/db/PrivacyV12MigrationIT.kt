package io.plady.moimyeon.storage.db

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager

@Tag("context")
@Testcontainers
class PrivacyV12MigrationIT {
    @Test
    fun `V36은 PostHog 처리를 담은 처리방침 v1_2를 2026-10-10 시행으로 추가한다`() {
        val url = prepareDatabase("publish")
        DriverManager.getConnection(url, "root", "root").use { connection ->
            migrate(url)

            val rows = snapshot(
                connection,
                "select required, status, effective_from, deleted_at from terms where type = 'PRIVACY' and version = 'v1.2'",
            )
            assertThat(rows).containsExactly(listOf("1", "ACTIVE", "2026-10-10 00:00:00", null))
            assertThat(snapshot(connection, "select content from terms where type = 'PRIVACY' and version = 'v1.2'").single().single())
                .contains("문서 버전: v1.2", "| PostHog |", "미국 / PostHog, Inc.", "서비스 이용 분석 기록", "2026년 10월 10일부터 시행")
                .doesNotContain("2026년 10월 7일")
        }
    }

    @Test
    fun `V36은 기존 약관과 동의 기록을 바꾸지 않는다`() {
        val url = prepareDatabase("preserve")
        DriverManager.getConnection(url, "root", "root").use { connection ->
            insertAgreement(connection)
            val terms = snapshot(connection, "select * from terms order by type, version")
            val agreements = snapshot(connection, "select * from terms_agreement")

            migrate(url)

            assertThat(snapshot(connection, "select * from terms where version <> 'v1.2' order by type, version")).isEqualTo(terms)
            assertThat(snapshot(connection, "select * from terms_agreement")).isEqualTo(agreements)
        }
    }

    @Test
    fun `같은 버전의 처리방침이 이미 있으면 V36은 실패하고 아무것도 바꾸지 않는다`() {
        val url = prepareDatabase("existing")
        DriverManager.getConnection(url, "root", "root").use { connection ->
            connection.createStatement().use {
                it.executeUpdate(
                    """
                    insert into terms (id, type, version, title, content, required, effective_from, status, created_at, updated_at)
                    values (X'00000000000000000000000000000009', 'PRIVACY', 'v1.2', 'existing', 'existing', true,
                            '2026-10-10 00:00:00', 'DRAFT', '2026-10-10 00:00:00', '2026-10-10 00:00:00')
                    """.trimIndent(),
                )
            }
            val terms = snapshot(connection, "select * from terms order by type, version")

            assertThatThrownBy { migrate(url) }.hasMessageContaining("uk_terms_type_version")

            assertThat(snapshot(connection, "select * from terms order by type, version")).isEqualTo(terms)
        }
    }

    private fun migrate(url: String) {
        Flyway.configure().dataSource(url, "root", "root").target("36").load().migrate()
    }

    private fun prepareDatabase(name: String): String {
        val rootUrl = "jdbc:mysql://${mysql.host}:${mysql.getMappedPort(3306)}"
        DriverManager.getConnection(rootUrl, "root", "root").use { connection ->
            connection.createStatement().use { it.executeUpdate("create database privacy_$name character set utf8mb4") }
        }
        val url = "$rootUrl/privacy_$name"
        Flyway.configure().dataSource(url, "root", "root").target("35").load().migrate()
        return url
    }

    private fun insertAgreement(connection: Connection) {
        connection.createStatement().use {
            it.executeUpdate(
                """
                insert into member (id, email, nickname, status, last_login_at, created_at, updated_at)
                values (X'00000000000000000000000000000001', 'privacy@example.com', 'privacy',
                        'ACTIVE', '2026-10-07 00:00:00', '2026-10-07 00:00:00', '2026-10-07 00:00:00')
                """.trimIndent(),
            )
            it.executeUpdate(
                """
                insert into terms_agreement (id, member_id, terms_id, agreed_at, created_at, updated_at)
                values (X'00000000000000000000000000000002', X'00000000000000000000000000000001',
                        X'd91d5a8f228a4a56874a88dd532febe7', '2026-10-07 00:00:00', '2026-10-07 00:00:00', '2026-10-07 00:00:00')
                """.trimIndent(),
            )
        }
    }

    private fun snapshot(connection: Connection, sql: String): List<List<String?>> = connection.createStatement().use { statement ->
        statement.executeQuery(sql).use { rows ->
            buildList {
                while (rows.next()) {
                    add((1..rows.metaData.columnCount).map { rows.getString(it) })
                }
            }
        }
    }

    companion object {
        @Container
        @JvmStatic
        private val mysql = GenericContainer(DockerImageName.parse("mysql:8.4.9"))
            .withEnv("MYSQL_DATABASE", "core")
            .withEnv("MYSQL_ROOT_PASSWORD", "root")
            .withExposedPorts(3306)
    }
}
