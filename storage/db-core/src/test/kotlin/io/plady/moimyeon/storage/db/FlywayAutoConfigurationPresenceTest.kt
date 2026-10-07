package io.plady.moimyeon.storage.db

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

class FlywayAutoConfigurationPresenceTest {
    @Test
    fun `Flyway 자동설정과 MySQL 방언 모듈이 런타임 클래스패스에 있다`() {
        listOf(
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
            "org.flywaydb.core.Flyway",
            "org.flywaydb.database.mysql.MySQLDatabaseType",
        ).forEach { className ->
            assertThatCode { Class.forName(className) }
                .describedAs("$className 이 없으면 dev/live 마이그레이션이 조용히 건너뛰어진다")
                .doesNotThrowAnyException()
        }
    }
}
