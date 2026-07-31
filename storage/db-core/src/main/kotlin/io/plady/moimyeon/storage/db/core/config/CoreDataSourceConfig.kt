package io.plady.moimyeon.storage.db.core.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
internal class CoreDataSourceConfig {
    @Bean
    @ConfigurationProperties(prefix = "storage.datasource.core")
    fun coreHikariConfig(): HikariConfig {
        return HikariConfig()
    }

    // @Primary: Flyway·JPA 자동설정은 DataSource 가 유일할 때만 알아서 고른다.
    // 두 번째 DataSource 가 생기는 순간 마이그레이션이 조용히 엉뚱한 DB 를 잡거나 아예 안 도는데,
    // 그 사고는 부팅 로그에 드러나지 않는다. 코어 DB 가 기본이라는 것을 여기서 못 박는다.
    @Bean
    @Primary
    fun coreDataSource(@Qualifier("coreHikariConfig") config: HikariConfig): HikariDataSource {
        return HikariDataSource(config)
    }
}
