dependencies {
    implementation(project(":core:core-api"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation(platform("software.amazon.awssdk:bom:${property("awsSdkVersion")}"))
    implementation("software.amazon.awssdk:s3")
}
