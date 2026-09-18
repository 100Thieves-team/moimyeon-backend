package io.plady.moimyeon.storage.db

import org.assertj.core.api.Assertions.assertThat
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
class RoomStatusLogMigrationIT {
    @Test
    fun `V26 데이터를 보존하고 V27 이후에도 구버전 INSERT를 허용한다`() {
        val url = "jdbc:mysql://${mysql.host}:${mysql.getMappedPort(3306)}/core"
        Flyway.configure().dataSource(url, "root", "root").target("26").load().migrate()
        DriverManager.getConnection(url, "root", "root").use { connection ->
            insertLegacyLog(connection, "CONFIRMED")
        }

        Flyway.configure().dataSource(url, "root", "root").load().migrate()

        DriverManager.getConnection(url, "root", "root").use { connection ->
            // rolling deployment 중인 구버전 서버는 새 컬럼을 전혀 모른다.
            insertLegacyLog(connection, "IN_PROGRESS")
            connection.createStatement().use { statement ->
                statement.executeQuery("select handler_type, handler_member_id from room_status_log order by id").use { rows ->
                    var count = 0
                    while (rows.next()) {
                        assertThat(rows.getString("handler_type")).isEqualTo("MEMBER")
                        assertThat(rows.getBytes("handler_member_id")).isNotNull()
                        count++
                    }
                    assertThat(count).isEqualTo(2)
                }
                statement.executeUpdate(
                    """
                    insert into room_status_log (
                        room_id, transition_type, handler_type, handler_member_id, occurred_at, created_at, updated_at
                    ) values (
                        unhex('00000000000000000000000000000001'), 'COMPLETED', 'SYSTEM', null, now(6), now(6), now(6)
                    )
                    """.trimIndent(),
                )
                statement.executeQuery("select handler_member_id from room_status_log where handler_type = 'SYSTEM'").use { rows ->
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getBytes("handler_member_id")).isNull()
                }
            }
        }
    }

    private fun insertLegacyLog(connection: Connection, transition: String) {
        connection.prepareStatement(
            """
            insert into room_status_log (
                room_id, transition_type, handler_member_id, occurred_at, created_at, updated_at
            ) values (
                unhex('00000000000000000000000000000001'), ?,
                unhex('00000000000000000000000000000002'), now(6), now(6), now(6)
            )
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, transition)
            statement.executeUpdate()
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
