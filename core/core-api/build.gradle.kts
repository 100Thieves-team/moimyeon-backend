import com.epages.restdocs.apispec.gradle.OpenApi3Extension
import com.epages.restdocs.apispec.gradle.OpenApi3Task
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import groovy.lang.Closure
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.servers.Server
import java.net.URLClassLoader

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
    enabled = true
}

dependencies {
    runtimeOnly(project(":admin:admin-api"))
    runtimeOnly(project(":clients:bedrock-client"))
    runtimeOnly(project(":storage:object-storage"))
    runtimeOnly(project(":storage:redis-core"))

    implementation(project(":core:core-enum"))
    implementation(project(":security:security-core"))
    implementation(project(":support:monitoring"))
    implementation(project(":support:logging"))
    implementation(project(":storage:db-core"))
    implementation(project(":clients:client-example"))

    testImplementation(project(":tests:api-docs"))
    testImplementation("org.springframework.boot:spring-boot-health")
    // 인증 실패(401) 문서화 테스트에서 실제 리소스서버 필터를 조립하기 위한 테스트 전용 의존성
    testImplementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // Boot 4 의 웹 직렬화는 Jackson 3(tools.jackson)이다. 루트가 깔아주는 kotlin 모듈은 Jackson 2 용이라
    // 웹 응답에 적용되지 않는다 — 없으면 Kotlin `is` 접두 프로퍼티(isPassed·isHost)가 bean 규칙으로
    // 접두가 잘린 이름(passed·host)으로 직렬화되어 REST Docs 계약과 실제 응답이 갈린다. (core-worker 와 같은 조치)
    implementation("tools.jackson.module:jackson-module-kotlin")
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
    format = "yaml"
    outputDirectory = layout.buildDirectory.dir("api-spec").get().asFile.path
    outputFileNamePrefix = "openapi3"
}

val openApiRuntimeClasspath = configurations.named("runtimeClasspath")

tasks.withType<OpenApi3Task>().configureEach {
    dependsOn("restDocsTest")
    dependsOn(":core:core-enum:jar")
    inputs.files(openApiRuntimeClasspath)
        .withPropertyName("openApiRuntimeClasspath")
        .withNormalizer(ClasspathNormalizer::class)
    doLast {
        val yamlFile = layout.buildDirectory.file("api-spec/openapi3.yaml").get().asFile
        val ymlFile = layout.buildDirectory.file("api-spec/openapi3.yml").get().asFile

        if (yamlFile.exists()) {
            patchGeneratedSchemas(
                yamlFile = yamlFile,
                stringEnumArrayProperties = resolveStringEnumArrayProperties(openApiRuntimeClasspath.get().files),
            )
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
// - 제한 문자열 스칼라 배열도 같은 제약이 있어 아이템 타입과 enum 값을 보강한다.
// - optional 객체·배열 필드: 생성기가 스칼라 optional 에는 nullable 을 붙이지만 객체·배열에는 붙이지 않는다.
//   이 서버는 null 필드를 생략하지 않고 그대로 직렬화하므로("region": null), optional = nullable 이다.
//   required 에 없는 객체·배열 프로퍼티 전부에 nullable 을 보강한다.
// - nullable 로 문서화한 필드는 생성기가 required 에서 빼므로, 항상 키를 반환하는 필드는 다시 넣는다.
// - multipart 요청 파트: 생성기가 비 JSON request body 를 스펙에 싣지 않아 직접 계약을 보강한다.
// - OAuth 로그인: Spring Security 필터 엔드포인트라 REST Docs 리소스가 없어 경로와 리다이렉트 계약을 보강한다.
val numberIdArrayProperties = setOf("interestCompanyIds", "interestJobRoleIds")
// 프로젝트 클래스는 Gradle 스크립트 컴파일 클래스패스에 없으므로 문서 생성 시 컴파일 산출물에서 enum 값을 읽는다.
val stringEnumArrayTypes = mapOf(
    "recentAttendances" to "io.plady.moimyeon.core.enums.AttendanceStatus",
)
fun resolveStringEnumArrayProperties(classpath: Set<File>): Map<String, List<String>> {
    val urls = classpath.map { it.toURI().toURL() }.toTypedArray()
    return URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { classLoader ->
        stringEnumArrayTypes.mapValues { (propertyName, className) ->
            val enumClass = classLoader.loadClass(className)
            check(enumClass.isEnum) { "$propertyName 보정 타입이 enum이 아니다: $className" }
            enumClass.enumConstants.map { (it as Enum<*>).name }
                .also { check(it.isNotEmpty()) { "$propertyName 보정 enum에 값이 없다: $className" } }
        }
    }
}

fun patchGeneratedSchemas(yamlFile: File, stringEnumArrayProperties: Map<String, List<String>>) {
    val mapper = Yaml.mapper()
    val root = mapper.readTree(yamlFile)
    var errorDataPatched = 0
    val numberArraysPatched = mutableSetOf<String>()
    val stringEnumArraysPatched = mutableSetOf<String>()
    var optionalCompositePatched = 0
    var nullableRequiredPatched = 0
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
        patchStringEnumArrays(schema, mapper, stringEnumArrayProperties, stringEnumArraysPatched)
        optionalCompositePatched += patchOptionalCompositeProperties(schema)
        nullableRequiredPatched += requireNullableProperty(schema, mapper, "activityTopPercent")
    }
    // 생성기 출력 형태가 바뀌어 보정 대상을 못 찾으면(예: $ref 공유 스키마로 전환) 조용히
    // 미보정 스펙이 나가지 않도록 빌드를 실패시킨다.
    check(errorDataPatched > 0) { "error.data 보정 대상을 스펙에서 찾지 못했다" }
    val missing = numberIdArrayProperties - numberArraysPatched
    check(missing.isEmpty()) { "스칼라 배열 보정 대상을 스펙에서 찾지 못했다: $missing" }
    val missingStringEnums = stringEnumArrayProperties.keys - stringEnumArraysPatched
    check(missingStringEnums.isEmpty()) { "문자열 enum 배열 보정 대상을 스펙에서 찾지 못했다: $missingStringEnums" }
    check(optionalCompositePatched > 0) { "optional 객체·배열 nullable 보정 대상을 스펙에서 찾지 못했다" }
    check(nullableRequiredPatched == 1) { "nullable 필수 필드 보정 대상이 하나가 아니다: $nullableRequiredPatched" }
    check(patchResumeMultipartRequest(root, mapper)) { "이력서 multipart 요청 보정 대상을 스펙에서 찾지 못했다" }
    check(patchOAuthLoginContract(root, mapper)) { "OAuth 로그인 OpenAPI 계약을 보정하지 못했다" }
    mapper.writeValue(yamlFile, root)
}

fun patchOAuthLoginContract(root: JsonNode, mapper: ObjectMapper): Boolean {
    val paths = root.path("paths") as? ObjectNode ?: return false
    val components = root.path("components") as? ObjectNode ?: return false
    val securitySchemes = components.path("securitySchemes") as? ObjectNode
        ?: mapper.createObjectNode().also { components.set<ObjectNode>("securitySchemes", it) }

    paths.set<ObjectNode>("/oauth2/authorization/google", googleOAuthStartPath(mapper))
    paths.set<ObjectNode>("/login/oauth2/code/google", googleOAuthCallbackPath(mapper))
    securitySchemes.set<ObjectNode>(
        "AccessTokenCookie",
        mapper.createObjectNode().apply {
            put("type", "apiKey")
            put("in", "cookie")
            put("name", "ACCESS_TOKEN")
            put("description", "웹 클라이언트에 발급되는 HttpOnly JWT 액세스 토큰 쿠키")
        },
    )
    securitySchemes.set<ObjectNode>(
        "BearerAuth",
        mapper.createObjectNode().apply {
            put("type", "http")
            put("scheme", "bearer")
            put("bearerFormat", "JWT")
            put("description", "앱 클라이언트용 Authorization: Bearer JWT")
        },
    )
    return true
}

fun googleOAuthStartPath(mapper: ObjectMapper): ObjectNode = mapper.createObjectNode().apply {
    putObject("get").apply {
        put("operationId", "googleOAuthStart")
        put("summary", "Google OAuth2 로그인 시작")
        put(
            "description",
            "브라우저 전체 페이지 이동으로 호출한다. 서버는 Google 동의 화면으로 302 리다이렉트한다.",
        )
        putArray("tags").add("Auth")
        putArray("security")
        putObject("responses").putObject("302").apply {
            put("description", "Google OAuth2 인가 엔드포인트로 이동")
            putObject("headers").set<ObjectNode>("Location", redirectLocationHeader(mapper))
        }
    }
}

fun googleOAuthCallbackPath(mapper: ObjectMapper): ObjectNode = mapper.createObjectNode().apply {
    putObject("get").apply {
        put("operationId", "googleOAuthCallback")
        put("summary", "Google OAuth2 로그인 콜백")
        put(
            "description",
            "Google 전용 콜백이다. 성공하면 ACCESS_TOKEN·REFRESH_TOKEN 쿠키를 함께 발급한 뒤 프론트 성공 " +
                "콜백으로 이동한다. Google 거절·state 검증 실패 또는 회원·세션 처리 실패 시 새 인증 쿠키 없이 " +
                "고정된 프론트 실패 URL로 이동하며 내부 원인은 노출하지 않는다.",
        )
        putArray("tags").add("Auth")
        putArray("security")
        putArray("parameters").apply {
            add(oauthCallbackQueryParameter(mapper, "code", "Google 인가 코드. 성공 콜백에서 전달"))
            add(oauthCallbackQueryParameter(mapper, "state", "로그인 요청 위변조 방지 상태값"))
            add(oauthCallbackQueryParameter(mapper, "error", "Google 실패 코드. 클라이언트로 전달하지 않음"))
            add(
                oauthCallbackQueryParameter(
                    mapper,
                    "error_description",
                    "Google 실패 상세. 로그·프론트 리다이렉트에 원문을 노출하지 않음",
                ),
            )
        }
        putObject("responses").putObject("302").apply {
            put("description", "성공 콜백 또는 고정된 실패 화면으로 이동")
            putObject("headers").apply {
                set<ObjectNode>("Location", redirectLocationHeader(mapper))
                putObject("Set-Cookie").apply {
                    put(
                        "description",
                        "성공 시 ACCESS_TOKEN과 REFRESH_TOKEN 두 헤더. 실패 시 발급하지 않음",
                    )
                    putObject("schema").apply {
                        put("type", "array")
                        putObject("items").put("type", "string")
                    }
                }
            }
        }
    }
}

fun oauthCallbackQueryParameter(mapper: ObjectMapper, name: String, description: String): ObjectNode {
    return mapper.createObjectNode().apply {
        put("name", name)
        put("in", "query")
        put("required", false)
        put("description", description)
        putObject("schema").put("type", "string")
    }
}

fun redirectLocationHeader(mapper: ObjectMapper): ObjectNode = mapper.createObjectNode().apply {
    put("description", "리다이렉트 대상 URI")
    put("required", true)
    putObject("schema").apply {
        put("type", "string")
        put("format", "uri")
    }
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

fun patchStringEnumArrays(
    node: JsonNode,
    mapper: ObjectMapper,
    stringEnumArrayProperties: Map<String, List<String>>,
    patched: MutableSet<String>,
) {
    if (node is ObjectNode) {
        node.fields().forEach { (name, value) ->
            val enumValues = stringEnumArrayProperties[name]
            if (enumValues != null && value is ObjectNode && value.path("type").asText() == "array") {
                patched += name
                value.set<ObjectNode>(
                    "items",
                    mapper.createObjectNode().apply {
                        put("type", "string")
                        putArray("enum").apply { enumValues.forEach { add(it) } }
                    },
                )
            }
            patchStringEnumArrays(value, mapper, stringEnumArrayProperties, patched)
        }
    } else if (node.isArray) {
        node.forEach { patchStringEnumArrays(it, mapper, stringEnumArrayProperties, patched) }
    }
}

fun patchOptionalCompositeProperties(node: JsonNode): Int {
    if (node !is ObjectNode) {
        if (node.isArray) return node.sumOf { patchOptionalCompositeProperties(it) }
        return 0
    }

    var patched = 0
    val properties = node.path("properties")
    if (properties is ObjectNode) {
        val required = (node.get("required") as? ArrayNode)?.map { it.asText() }?.toSet() ?: emptySet()
        properties.fields().forEach { (name, property) ->
            if (
                property is ObjectNode &&
                property.path("type").asText() in setOf("object", "array") &&
                name !in required &&
                !property.path("nullable").asBoolean(false)
            ) {
                property.put("nullable", true)
                patched++
            }
        }
    }
    node.forEach { patched += patchOptionalCompositeProperties(it) }
    return patched
}

fun requireNullableProperty(node: JsonNode, mapper: ObjectMapper, propertyName: String): Int {
    if (node !is ObjectNode) {
        if (node.isArray) return node.sumOf { requireNullableProperty(it, mapper, propertyName) }
        return 0
    }

    var patched = 0
    val property = node.path("properties").path(propertyName)
    if (property.path("nullable").asBoolean(false)) {
        val required = node.get("required") as? ArrayNode ?: mapper.createArrayNode().also {
            node.set<ArrayNode>("required", it)
        }
        if (required.none { it.asText() == propertyName }) required.add(propertyName)
        patched++
    }
    node.forEach { patched += requireNullableProperty(it, mapper, propertyName) }
    return patched
}
repositories {
    mavenCentral()
}
