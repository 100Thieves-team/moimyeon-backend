import com.epages.restdocs.apispec.gradle.OpenApi3Extension
import com.epages.restdocs.apispec.gradle.OpenApi3Task

plugins {
    id("com.epages.restdocs-api-spec")
}

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
    implementation(project(":storage:db-core"))
    implementation(project(":clients:client-example"))

    testImplementation(project(":tests:api-docs"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
}

configure<OpenApi3Extension> {
    setServer("http://localhost:8080")
    title = "Moimyeon API"
    description = "Moimyeon REST API contract generated from Spring REST Docs"
    version = project.version.toString()
    format = "yaml"
    outputDirectory = layout.buildDirectory.dir("api-spec").get().asFile.path
    outputFileNamePrefix = "openapi3"
}

tasks.withType<OpenApi3Task>().configureEach {
    dependsOn("restDocsTest")
    doLast {
        val yamlFile = layout.buildDirectory.file("api-spec/openapi3.yaml").get().asFile
        val ymlFile = layout.buildDirectory.file("api-spec/openapi3.yml").get().asFile

        if (yamlFile.exists()) {
            yamlFile.copyTo(target = ymlFile, overwrite = true)
        }
    }
}
