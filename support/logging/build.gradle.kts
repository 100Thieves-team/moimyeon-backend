plugins {
    `java-library`
}

dependencies {
    api("io.github.oshai:kotlin-logging-jvm:${property("kotlinLoggingVersion")}")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("io.sentry:sentry-spring-boot-4-starter:${property("sentryVersion")}")
    implementation("io.sentry:sentry-logback:${property("sentryVersion")}")
}

tasks.withType<Test>().configureEach {
    systemProperty("logging.test.classpath", sourceSets.test.get().runtimeClasspath.asPath)
}
