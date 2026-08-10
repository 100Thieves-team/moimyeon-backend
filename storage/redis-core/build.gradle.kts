dependencies {
    implementation(project(":core:core-api"))
    implementation(project(":core:core-enum"))
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    testImplementation("org.testcontainers:testcontainers-junit-jupiter:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:testcontainers:${property("testcontainersVersion")}")
}
