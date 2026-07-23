import com.epages.restdocs.apispec.gradle.OpenApi3Extension
import com.epages.restdocs.apispec.gradle.OpenApi3Task
import com.epages.restdocs.apispec.gradle.PluginOauth2Configuration
import groovy.lang.Closure
import io.swagger.v3.oas.models.servers.Server

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
    // dev 브랜치 기준 API 가 서빙되는 호스트들. Swagger UI 의 서버 드롭다운으로 노출된다.
    @Suppress("UNCHECKED_CAST")
    setServers(
        listOf(
            closureOf<Server> { url = "http://localhost:8080" },
            closureOf<Server> { url = "https://api-dev.moimyeon.plady.io" },
        ) as List<Closure<Server>>,
    )
    title = "Moimyeon API"
    description = "Moimyeon REST API contract generated from Spring REST Docs. " +
        "로그인은 Google OAuth2 리다이렉트 플로우로 시작하며(GET /oauth2/authorization/google), " +
        "성공 시 ACCESS_TOKEN·REFRESH_TOKEN 쿠키가 발급된다. " +
        "API 인증은 ACCESS_TOKEN 쿠키 또는 Authorization: Bearer 헤더 둘 다 허용한다."
    version = project.version.toString()
    // 로그인 플로우는 컨트롤러가 아니라 Spring Security 필터 체인이 처리하므로
    // 테스트 기반 paths 대신 securityScheme 으로 선언한다.
    // 상대 경로: OpenAPI 3 규격상 선택된 server URL 기준으로 해석되어 환경별 분기가 필요 없다.
    oauth2SecuritySchemeDefinition = PluginOauth2Configuration().apply {
        flows = arrayOf("authorizationCode")
        authorizationUrl = "/oauth2/authorization/google"
        tokenUrl = "/login/oauth2/code/google"
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
