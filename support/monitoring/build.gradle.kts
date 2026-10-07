dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    testImplementation("io.micrometer:micrometer-registry-otlp")
    // Matches the runtime protocol dependency of Micrometer 1.17.0 for wire-format assertions.
    testImplementation("io.opentelemetry.proto:opentelemetry-proto:1.10.0-alpha")
}
