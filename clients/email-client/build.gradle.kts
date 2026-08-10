dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-worker"))
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation(platform("software.amazon.awssdk:bom:${property("awsSdkVersion")}"))
    implementation("software.amazon.awssdk:sesv2")
}
