dependencies {
    implementation(project(":core:core-enum")) // 포트(SocialMemberResolver) 시그니처의 SocialLoginProvider

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server") // 자체 JWT 발급(JwtEncoder)
    compileOnly("org.springframework.boot:spring-boot-starter-webmvc")
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework:spring-test")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
}
