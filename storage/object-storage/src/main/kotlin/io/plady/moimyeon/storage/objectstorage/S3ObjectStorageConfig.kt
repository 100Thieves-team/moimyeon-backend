package io.plady.moimyeon.storage.objectstorage

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

@Profile("local-dev", "dev", "staging", "live")
@Configuration
internal class S3ObjectStorageConfig {
    @Bean
    fun s3Client(properties: S3ObjectStorageProperties): S3Client {
        return S3Client.builder()
            .region(Region.of(properties.region))
            .build()
    }
}
