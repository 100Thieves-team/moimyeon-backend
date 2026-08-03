package io.plady.moimyeon.storage.objectstorage

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Profile
import java.time.Duration

@ConfigurationProperties("storage.object-storage.s3")
@Profile("local-dev", "dev", "staging", "live")
data class S3ObjectStorageProperties(
    val bucket: String,
    val region: String,
    val apiCallTimeout: Duration,
    val apiCallAttemptTimeout: Duration,
)
