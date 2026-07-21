tasks.named<Jar>("bootJar").configure {
    enabled = true
}

tasks.named<Jar>("jar").configure {
    enabled = false
}

dependencies {
    runtimeOnly(project(":admin:admin-api"))

    implementation(project(":core:core-enum"))
    implementation(project(":security:security-core"))
    implementation(project(":support:monitoring"))
    implementation(project(":support:logging"))
    runtimeOnly(project(":storage:db-core"))
    implementation(project(":clients:client-example"))

    testImplementation(project(":tests:api-docs"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
}
