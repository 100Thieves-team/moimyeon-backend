import com.epages.restdocs.apispec.gradle.OpenApi3Extension
import com.epages.restdocs.apispec.gradle.OpenApi3Task
import com.epages.restdocs.apispec.gradle.PluginOauth2Configuration
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import groovy.lang.Closure
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.servers.Server

buildscript {
    dependencies {
        // 생성된 스펙을 OpenAPI 3.0 규칙으로 파싱 검증하는 게이트 (validateOpenApiSpec)
        classpath("io.swagger.parser.v3:swagger-parser:2.1.31")
    }
}

plugins {
    id("com.epages.restdocs-api-spec")
    kotlin("kapt")
}

tasks.named<Jar>("bootJar").configure {
    enabled = true
}

tasks.named<Jar>("jar").configure {
    enabled = false
}

dependencies {
    runtimeOnly(project(":admin:admin-api"))
    runtimeOnly(project(":clients:client-ai"))
    runtimeOnly(project(":storage:object-storage"))

    implementation(project(":core:core-domain"))
    implementation(project(":core:core-enum"))
    implementation(project(":security:security-core"))
    implementation(project(":support:monitoring"))
    implementation(project(":support:logging"))
    implementation(project(":storage:db-core"))
    implementation(project(":clients:client-example"))

    testImplementation(project(":tests:api-docs"))
    // 인증 실패(401) 문서화 테스트에서 실제 리소스서버 필터를 조립하기 위한 테스트 전용 의존성
    testImplementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // 채용 링크 즉시 추가(BE-03/MOI-334): 서버측 OG 태그 fetch·파싱. HTML 스크래핑이라 Feign(JSON) 대신 Jsoup 을 쓴다.
    implementation("org.jsoup:jsoup:${property("jsoupVersion")}")
    testImplementation(kotlin("test"))
}

configure<OpenApi3Extension> {
    // dev 브랜치 기준 API 가 서빙되는 호스트들. Swagger UI 의 서버 드롭다운으로 노출된다.
    @Suppress("UNCHECKED_CAST")
    setServers(
        listOf(
            closureOf<Server> { url = "http://localhost:8080" },
            closureOf<Server> { url = "https://api.dev.moimyeon.plady.io" },
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
            patchGeneratedSchemas(yamlFile)
            validateOpenApiSpec(yamlFile)
            yamlFile.copyTo(target = ymlFile, overwrite = true)
        }
    }
}

// 생성기가 3.0 에 유효하지 않은 스펙을 내는 회귀를 빌드에서 잡는다. swagger-parser 는 속성 누락 등
// 구조 위반을 잡지만 oneOf 안의 bare null(실제로 샜던 형태)은 조용히 삼키므로 트리 검사로 보강한다.
// 표현이 부족한 스키마(oneOf 만능 타입 등)까지 걸러주지는 못한다. 그건 필드 문서화 소관.
fun validateOpenApiSpec(yamlFile: File) {
    val text = yamlFile.readText()
    val problems = mutableListOf<String>()
    problems += io.swagger.parser.OpenAPIParser().readContents(text, null, null).messages.orEmpty()
    collectNonObjectComposedSchemas(Yaml.mapper().readTree(text), "$", problems)
    if (problems.isNotEmpty()) {
        throw GradleException(
            "생성된 ${yamlFile.name} 이 OpenAPI 3.0 규칙을 위반한다:\n" + problems.joinToString("\n"),
        )
    }
}

// oneOf/anyOf/allOf 의 원소는 항상 스키마 객체여야 한다 (OpenAPI 3.0 에는 null 타입이 없다)
fun collectNonObjectComposedSchemas(node: JsonNode, path: String, problems: MutableList<String>) {
    if (node.isObject) {
        node.fields().forEach { (name, value) ->
            if (name in setOf("oneOf", "anyOf", "allOf") && value.isArray) {
                value.forEachIndexed { i, element ->
                    if (!element.isObject) {
                        problems += "$path.$name[$i] 이 스키마 객체가 아니다: $element"
                    }
                }
            }
            collectNonObjectComposedSchemas(value, "$path.$name", problems)
        }
    } else if (node.isArray) {
        node.forEachIndexed { i, element -> collectNonObjectComposedSchemas(element, "$path[$i]", problems) }
    }
}

// restdocs-api-spec 필드 문서화로 표현할 수 없는 OpenAPI 3.0 계약을 생성 후 보정한다.
// - error.data: "필드명 -> 사유" 맵이라 additionalProperties 스키마가 필요
// - 숫자 id 스칼라 배열: 아이템 타입 문서화(a[] + 타입)를 생성기가 지원하지 않음.
//   요청에서는 최상위 프로퍼티, 응답에서는 data 아래에 나타나므로 트리 전체를 훑는다.
// - multipart 요청 파트: 생성기가 비 JSON request body 를 스펙에 싣지 않아 직접 계약을 보강한다.
val numberIdArrayProperties = setOf("interestCompanyIds", "interestJobRoleIds")

fun patchGeneratedSchemas(yamlFile: File) {
    val mapper = Yaml.mapper()
    val root = mapper.readTree(yamlFile)
    var errorDataPatched = 0
    val numberArraysPatched = mutableSetOf<String>()
    root.path("components").path("schemas").forEach { schema ->
        val errorData = schema.path("properties").path("error").path("properties").path("data")
        if (errorData is ObjectNode) {
            errorDataPatched++
            errorData.remove("oneOf")
            errorData.put("type", "object")
            errorData.put("nullable", true)
            errorData.set<ObjectNode>("additionalProperties", mapper.createObjectNode().put("type", "string"))
        }
        patchNumberIdArrays(schema, mapper, numberArraysPatched)
    }
    // 생성기 출력 형태가 바뀌어 보정 대상을 못 찾으면(예: $ref 공유 스키마로 전환) 조용히
    // 미보정 스펙이 나가지 않도록 빌드를 실패시킨다.
    check(errorDataPatched > 0) { "error.data 보정 대상을 스펙에서 찾지 못했다" }
    val missing = numberIdArrayProperties - numberArraysPatched
    check(missing.isEmpty()) { "스칼라 배열 보정 대상을 스펙에서 찾지 못했다: $missing" }
    check(patchResumeMultipartRequest(root, mapper)) { "이력서 multipart 요청 보정 대상을 스펙에서 찾지 못했다" }
    mapper.writeValue(yamlFile, root)
}

fun patchResumeMultipartRequest(root: JsonNode, mapper: ObjectMapper): Boolean {
    val post = root.path("paths").path("/v1/members/me/resumes").path("post")
    if (post !is ObjectNode) {
        return false
    }

    val schema = mapper.createObjectNode().apply {
        put("type", "object")
        putArray("required").add("file")
        putObject("properties").apply {
            putObject("file").apply {
                put("type", "string")
                put("format", "binary")
                put("description", "PDF 이력서 파일 (application/pdf, 1byte~10MB)")
            }
        }
    }
    val multipart = mapper.createObjectNode().apply {
        set<ObjectNode>("schema", schema)
        putObject("encoding").apply {
            putObject("file").put("contentType", "application/pdf")
        }
    }
    val requestBody = mapper.createObjectNode().apply {
        put("required", true)
        putObject("content").set<ObjectNode>("multipart/form-data", multipart)
    }
    post.set<ObjectNode>("requestBody", requestBody)
    return true
}

fun patchNumberIdArrays(node: JsonNode, mapper: ObjectMapper, patched: MutableSet<String>) {
    if (node is ObjectNode) {
        node.fields().forEach { (name, value) ->
            if (name in numberIdArrayProperties && value is ObjectNode && value.path("type").asText() == "array") {
                patched += name
                value.set<ObjectNode>("items", mapper.createObjectNode().put("type", "number"))
            }
            patchNumberIdArrays(value, mapper, patched)
        }
    } else if (node.isArray) {
        node.forEach { patchNumberIdArrays(it, mapper, patched) }
    }
}
repositories {
    mavenCentral()
}
