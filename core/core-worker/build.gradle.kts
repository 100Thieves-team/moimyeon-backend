tasks.named<Jar>("bootJar").configure {
    enabled = true
}

tasks.named<Jar>("jar").configure {
    enabled = false
}

dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":storage:redis-core")) {
        isTransitive = false
    }
    implementation(project(":support:monitoring"))
    implementation(project(":support:logging"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
}
