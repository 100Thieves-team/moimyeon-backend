dependencies {
    implementation(project(":core:core-domain"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(platform("software.amazon.awssdk:bom:${property("awsSdkVersion")}"))
    implementation("software.amazon.awssdk:s3")
}
