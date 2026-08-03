dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

dependencies {
    implementation(project(":core:core-domain"))
    implementation("org.springframework.ai:spring-ai-starter-model-bedrock-converse:${property("springAiVersion")}")
    implementation("org.apache.pdfbox:pdfbox:${property("pdfBoxVersion")}")
}
