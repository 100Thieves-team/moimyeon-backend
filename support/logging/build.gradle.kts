dependencies {
    implementation("io.sentry:sentry-spring-boot-4-starter:${property("sentryVersion")}")
    implementation("io.sentry:sentry-logback:${property("sentryVersion")}")
}
