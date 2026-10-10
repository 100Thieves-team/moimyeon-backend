plugins {
    kotlin("jvm")
    kotlin("plugin.spring") apply false
    kotlin("plugin.jpa") apply false
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
    id("org.asciidoctor.jvm.convert") apply false
    id("org.jlleitschuh.gradle.ktlint") apply false
    id("com.epages.restdocs-api-spec") apply false
}

allprojects {
    group = "${property("projectGroup")}"
    version = "${property("applicationVersion")}"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.asciidoctor.jvm.convert")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    dependencyManagement {
        imports {
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudDependenciesVersion")}")
        }
    }

    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
        annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
        testImplementation("org.springframework.boot:spring-boot-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testImplementation("org.assertj:assertj-core")
        testImplementation("com.ninja-squad:springmockk:${property("springMockkVersion")}")
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of("${property("javaVersion")}")
        }
    }

    kotlin {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        }
    }

    tasks.named<Jar>("bootJar").configure {
        enabled = false
    }

    tasks.named<Jar>("jar").configure {
        enabled = true
    }

    tasks.test {
        useJUnitPlatform {
            excludeTags("develop", "restdocs")
        }
    }

    // REST Docs 테스트는 캐시 출력으로 선언되지 않은 문서 조각(build/generated-snippets)을 만든다. 결과만 되살리면
    // 조각이 없어 openapi3·asciidoctor가 빈 스펙을 만든다(2026-10-10 main API Docs Pages 실패). 매번 실행한다.
    tasks.matching { it.name == "restDocsTest" }.configureEach {
        outputs.doNotCacheIf("REST Docs 테스트의 문서 조각은 캐시가 되돌리지 않는다") { true }
    }

    // Testcontainers 통합 테스트는 외부 컨테이너 상태에 의존하므로 결과를 재사용하지 않는다(MOI-593).
    // 직접 선언한 테스트 의존만 본다. 공용 테스트 모듈을 거쳐 Testcontainers를 가져오면 여기에 조건을 더한다.
    tasks.withType<Test>().configureEach {
        outputs.doNotCacheIf("Testcontainers 통합 테스트는 외부 컨테이너 상태에 의존한다") {
            project.configurations.getByName("testRuntimeClasspath").allDependencies
                .any { it.group == "org.testcontainers" }
        }
    }

    testing {
        suites {
            named<JvmTestSuite>("test") {
                targets {
                    register("unitTest") {
                        testTask.configure {
                            group = "verification"
                            useJUnitPlatform {
                                excludeTags("develop", "context", "restdocs")
                            }
                        }
                    }

                    register("contextTest") {
                        testTask.configure {
                            group = "verification"
                            useJUnitPlatform {
                                includeTags("context")
                            }
                        }
                    }

                    register("restDocsTest") {
                        testTask.configure {
                            group = "verification"
                            useJUnitPlatform {
                                includeTags("restdocs")
                            }
                        }
                    }

                    register("developTest") {
                        testTask.configure {
                            group = "verification"
                            useJUnitPlatform {
                                includeTags("develop")
                            }
                        }
                    }
                }
            }
        }
    }

    tasks.named("asciidoctor").configure {
        dependsOn("restDocsTest")
    }
}
