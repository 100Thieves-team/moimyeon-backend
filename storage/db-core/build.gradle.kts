allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

dependencies {
    api(project(":core:core-enum"))

    api("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("com.h2database:h2")

    // 영속 DB(dev/staging/live)의 스키마 마이그레이션. 로컬·테스트(H2)는 schema.sql 이 담당하므로
    // spring.flyway.enabled=false 이고, 그때 이 의존성은 아무 일도 하지 않는다.
    // spring-boot-flyway 는 Boot 4 에서 분리된 자동설정 모듈, flyway-mysql 은 MySQL 방언 모듈이다.
    // 셋 다 있어야 마이그레이션이 실행된다.
    runtimeOnly("org.springframework.boot:spring-boot-flyway")
    runtimeOnly("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-mysql")

    testImplementation("org.testcontainers:testcontainers-junit-jupiter:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:testcontainers:${property("testcontainersVersion")}")
}
