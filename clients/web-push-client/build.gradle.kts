dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-worker"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("com.google.firebase:firebase-admin:${property("firebaseAdminVersion")}")
}
