tasks.named<Jar>("bootJar").configure {
    enabled = true
}

tasks.named<Jar>("jar").configure {
    enabled = true
}

dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":storage:db-core"))
    implementation(project(":storage:redis-core")) {
        isTransitive = false
    }
    implementation(project(":support:monitoring"))
    implementation(project(":support:logging"))
    runtimeOnly(project(":clients:email-client"))
    runtimeOnly(project(":clients:web-push-client"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("tools.jackson.module:jackson-module-kotlin")

    testImplementation("org.testcontainers:testcontainers-junit-jupiter:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:testcontainers:${property("testcontainersVersion")}")
    testImplementation("io.micrometer:micrometer-core")
}
