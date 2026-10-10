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
class OverseasTransferTermsMigrationIT {
    @Test
    fun `V36은 개인정보 국외 이전 필수 약관을 활성 상태로 추가한다`() {
        val url = prepareDatabase("terms")
        DriverManager.getConnection(url, "root", "root").use { connection ->
            migrate(url)

            val rows = snapshot(
                connection,
                "select version, required, status, effective_from, deleted_at from terms where type = 'OVERSEAS_TRANSFER'",
            )
            assertThat(rows).containsExactly(listOf("v1.0", "1", "ACTIVE", "2026-10-10 00:00:00", null))
            assertThat(snapshot(connection, "select content from terms where type = 'OVERSEAS_TRANSFER'").single().single())
                .contains("PostHog, Inc.", "미국", "가명 처리된 회원 식별자")
        }
    }

    @Test
    fun `V36은 기존 회원 전원에게 국외 이전 약관 동의 기록을 남긴다`() {
        val url = prepareDatabase("agreements")
        DriverManager.getConnection(url, "root", "root").use { connection ->
            insertMember(connection, "00000000000000000000000000000001", deletedAt = null)
            insertMember(connection, "00000000000000000000000000000002", deletedAt = "2026-10-09 00:00:00")

            migrate(url)

            val agreed = snapshot(
                connection,
                "select hex(a.member_id) from terms_agreement a join terms t on t.id = a.terms_id " +
                    "where t.type = 'OVERSEAS_TRANSFER' and a.deleted_at is null order by a.member_id",
            )
            assertThat(agreed.map { it.single() }).containsExactly(
                "00000000000000000000000000000001",
                "00000000000000000000000000000002",
            )
        }
    }

    @Test
    fun `같은 종류와 버전의 약관이 이미 있으면 V36은 실패하고 기존 약관과 동의 기록을 바꾸지 않는다`() {
        val url = prepareDatabase("existing")
        DriverManager.getConnection(url, "root", "root").use { connection ->
            insertMember(connection, "00000000000000000000000000000001", deletedAt = null)
            connection.createStatement().use {
                it.executeUpdate(
                    """
                    insert into terms (id, type, version, title, content, required, effective_from, status, created_at, updated_at)
                    values (X'00000000000000000000000000000009', 'OVERSEAS_TRANSFER', 'v1.0', 'existing', 'existing', true,
                            '2026-10-10 00:00:00', 'ACTIVE', '2026-10-10 00:00:00', '2026-10-10 00:00:00')
                    """.trimIndent(),
                )
            }
            val terms = snapshot(connection, "select * from terms order by type, version")
            val agreements = snapshot(connection, "select * from terms_agreement")

            assertThatThrownBy { migrate(url) }.hasMessageContaining("uk_terms_type_version")

            assertThat(snapshot(connection, "select * from terms order by type, version")).isEqualTo(terms)
            assertThat(snapshot(connection, "select * from terms_agreement")).isEqualTo(agreements)
        }
    }

    private fun migrate(url: String) {
        Flyway.configure().dataSource(url, "root", "root").target("36").load().migrate()
    }

    private fun prepareDatabase(name: String): String {
        val rootUrl = "jdbc:mysql://${mysql.host}:${mysql.getMappedPort(3306)}"
        DriverManager.getConnection(rootUrl, "root", "root").use { connection ->
            connection.createStatement().use { it.executeUpdate("create database overseas_$name character set utf8mb4") }
        }
        val url = "$rootUrl/overseas_$name"
        Flyway.configure().dataSource(url, "root", "root").target("35").load().migrate()
        return url
    }

    private fun insertMember(connection: Connection, id: String, deletedAt: String?) {
        connection.prepareStatement(
            """
            insert into member (id, email, nickname, status, last_login_at, created_at, updated_at, deleted_at)
            values (unhex(?), concat('member-', ?, '@example.com'), concat('m', right(?, 6)),
                    'ACTIVE', '2026-10-07 00:00:00', '2026-10-07 00:00:00', '2026-10-07 00:00:00', ?)
            """.trimIndent(),
        ).use {
            it.setString(1, id)
            it.setString(2, id)
            it.setString(3, id)
            it.setString(4, deletedAt)
            it.executeUpdate()
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
