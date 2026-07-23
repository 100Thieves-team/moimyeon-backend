dependencies {
    api("com.epages:restdocs-api-spec-mockmvc:${property("restdocsApiSpecVersion")}")
    api("org.springframework.boot:spring-boot-restdocs")
    api("org.springframework.restdocs:spring-restdocs-mockmvc")
    compileOnly("org.jetbrains.kotlin:kotlin-test-junit5")
}
