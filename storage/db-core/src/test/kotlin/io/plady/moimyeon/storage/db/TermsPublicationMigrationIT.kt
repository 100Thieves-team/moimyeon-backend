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
class TermsPublicationMigrationIT {
    @Test
    fun `초안을 발행해도 이전 문서와 동의 이력은 그대로 남는다`() {
        val url = prepareDatabase("publication")
        DriverManager.getConnection(url, "root", "root").use { connection ->
            insertAgreement(connection, "019daf00000070008000000000000001")
            val previous = snapshot(connection, "select * from terms where version = 'v1.0' order by type")
            val agreements = snapshot(connection, "select * from terms_agreement")

            Flyway.configure().dataSource(url, "root", "root").target("34").load().migrate()

            assertThat(snapshot(connection, "select * from terms where version = 'v1.0' order by type")).isEqualTo(previous)
            assertThat(snapshot(connection, "select * from terms_agreement")).isEqualTo(agreements)
            connection.createStatement().use { statement ->
                statement.executeQuery("select status, content, effective_from from terms where version = 'v1.1' order by type").use { rows ->
                    var count = 0
                    while (rows.next()) {
                        assertThat(rows.getString("status")).isEqualTo("ACTIVE")
                        assertThat(rows.getString("content")).contains("이유제", "010-9328-9628")
                            .doesNotContain("미발행 검토안", "시행 예정일", "[내부 메모:")
                        assertThat(rows.getString("content").toByteArray(Charsets.UTF_8).size).isBetween(8_000, 65_535)
                        assertThat(rows.getTimestamp("effective_from").toLocalDateTime().toString()).isEqualTo("2026-10-08T00:00")
                        count++
                    }
                    assertThat(count).isEqualTo(2)
                }
            }
            assertThat(Flyway.configure().dataSource(url, "root", "root").target("34").load().migrate().migrationsExecuted).isZero()
        }
    }

    @Test
    fun `초안에 동의 기록이 있으면 어느 본문도 덮어쓰지 않는다`() {
        assertPublicationRejected("agreed")
    }

    @Test
    fun `이미 발행된 본문이 있으면 어느 본문도 덮어쓰지 않는다`() {
        assertPublicationRejected("published")
    }

    @Test
    fun `초안이 삭제되어 있으면 어느 본문도 덮어쓰지 않는다`() {
        assertPublicationRejected("deleted")
    }

    @Test
    fun `초안이 누락되어 있으면 어느 본문도 덮어쓰지 않는다`() {
        assertPublicationRejected("missing")
    }

    @Test
    fun `시행일을 7일로 정정해도 문서 ID와 기존 약관 및 동의 기록은 유지한다`() {
        val url = prepareDatabase("effective_date", "34")
        DriverManager.getConnection(url, "root", "root").use { connection ->
            insertAgreement(connection, "019daf00000070008000000000000001")
            val previous = snapshot(connection, "select * from terms where version = 'v1.0' order by type")
            val agreements = snapshot(connection, "select * from terms_agreement")
            val sql = "select hex(id), type, version, title, content, required, effective_from, status, created_at, deleted_at " +
                "from terms where version = 'v1.1' order by type"
            val before = snapshot(connection, sql)

            Flyway.configure().dataSource(url, "root", "root").target("35").load().migrate()

            assertThat(before).hasSize(2)
            assertThat(snapshot(connection, sql)).isEqualTo(
                before.map { row ->
                    row.mapIndexed { index, value ->
                        when (index) {
                            4 -> value?.replace("2026년 10월 8일", "2026년 10월 7일")
                            6 -> value?.replace("2026-10-08", "2026-10-07")
                            else -> value
                        }
                    }
                },
            )
            assertThat(snapshot(connection, "select * from terms where version = 'v1.0' order by type")).isEqualTo(previous)
            assertThat(snapshot(connection, "select * from terms_agreement")).isEqualTo(agreements)
            assertThat(Flyway.configure().dataSource(url, "root", "root").target("35").load().migrate().migrationsExecuted).isZero()
        }
    }

    @Test
    fun `예약 문서에 동의 이력이 있으면 두 문서의 시행일과 본문 모두 정정하지 않는다`() {
        val url = prepareDatabase("effective_date_agreed", "34")
        DriverManager.getConnection(url, "root", "root").use { connection ->
            insertAgreement(connection, "f614c0bb87b544e8baf12b8b2ad4c1b2")
            val terms = snapshot(connection, "select * from terms order by type, version")
            val agreements = snapshot(connection, "select * from terms_agreement")

            assertThatThrownBy { Flyway.configure().dataSource(url, "root", "root").target("35").load().migrate() }
                .hasMessageContaining("ABORT_terms_v1_1_effective_date_requires_unagreed_terms")

            assertThat(snapshot(connection, "select * from terms order by type, version")).isEqualTo(terms)
            assertThat(snapshot(connection, "select * from terms_agreement")).isEqualTo(agreements)
        }
    }

    private fun assertPublicationRejected(condition: String) {
        val url = prepareDatabase(condition)
        DriverManager.getConnection(url, "root", "root").use { connection ->
            connection.createStatement().use { statement ->
                when (condition) {
                    "agreed" -> insertAgreement(connection, "f614c0bb87b544e8baf12b8b2ad4c1b2")
                    "published" -> statement.executeUpdate("update terms set status = 'ACTIVE' where type = 'SERVICE' and version = 'v1.1'")
                    "deleted" -> statement.executeUpdate("update terms set deleted_at = '2026-10-07 00:00:00' where type = 'SERVICE' and version = 'v1.1'")
                    "missing" -> statement.executeUpdate("delete from terms where type = 'SERVICE' and version = 'v1.1'")
                }
            }
            val terms = snapshot(connection, "select * from terms order by type, version")
            val agreements = snapshot(connection, "select * from terms_agreement")

            assertThatThrownBy { Flyway.configure().dataSource(url, "root", "root").target("34").load().migrate() }
                .hasMessageContaining("ABORT_terms_v1_1_must_be_unagreed_drafts")

            assertThat(snapshot(connection, "select * from terms order by type, version")).isEqualTo(terms)
            assertThat(snapshot(connection, "select * from terms_agreement")).isEqualTo(agreements)
        }
    }

    private fun prepareDatabase(name: String, target: String = "33"): String {
        val rootUrl = "jdbc:mysql://${mysql.host}:${mysql.getMappedPort(3306)}"
        DriverManager.getConnection(rootUrl, "root", "root").use { connection ->
            connection.createStatement().use { it.executeUpdate("create database terms_$name character set utf8mb4") }
        }
        val url = "$rootUrl/terms_$name"
        Flyway.configure().dataSource(url, "root", "root").target(target).load().migrate()
        return url
    }

    private fun insertAgreement(connection: Connection, termsId: String) {
        connection.createStatement().use {
            it.executeUpdate(
                """
                insert into member (id, email, nickname, status, last_login_at, created_at, updated_at)
                values (X'00000000000000000000000000000001', 'publication@example.com', 'publication',
                        'ACTIVE', '2026-10-07 00:00:00', '2026-10-07 00:00:00', '2026-10-07 00:00:00')
                """.trimIndent(),
            )
        }
        connection.prepareStatement(
            """
            insert into terms_agreement (id, member_id, terms_id, agreed_at, created_at, updated_at)
            values (X'00000000000000000000000000000002', X'00000000000000000000000000000001',
                    unhex(?), '2026-10-07 00:00:00', '2026-10-07 00:00:00', '2026-10-07 00:00:00')
            """.trimIndent(),
        ).use {
            it.setString(1, termsId)
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
