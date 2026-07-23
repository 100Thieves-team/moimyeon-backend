import com.epages.restdocs.apispec.gradle.OpenApi3Extension
import com.epages.restdocs.apispec.gradle.OpenApi3Task
import com.epages.restdocs.apispec.gradle.PluginOauth2Configuration

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
    description = "Moimyeon REST API contract generated from Spring REST Docs. " +
        "로그인은 Google OAuth2 리다이렉트 플로우로 시작하며(GET /oauth2/authorization/google), " +
        "성공 시 ACCESS_TOKEN·REFRESH_TOKEN 쿠키가 발급된다. " +
        "API 인증은 ACCESS_TOKEN 쿠키 또는 Authorization: Bearer 헤더 둘 다 허용한다."
    version = project.version.toString()
    // 로그인 플로우는 컨트롤러가 아니라 Spring Security 필터 체인이 처리하므로
    // 테스트 기반 paths 대신 securityScheme 으로 선언한다.
    oauth2SecuritySchemeDefinition = PluginOauth2Configuration().apply {
        flows = arrayOf("authorizationCode")
        authorizationUrl = "http://localhost:8080/oauth2/authorization/google"
        tokenUrl = "http://localhost:8080/login/oauth2/code/google"
    }
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
