package io.plady.moimyeon.storage.objectstorage

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Profile("local-dev", "dev", "staging", "live")
@Configuration
internal class S3ObjectStorageConfig {
    @Bean
    fun s3Client(properties: S3ObjectStorageProperties): S3Client {
        return S3Client.builder()
            .region(Region.of(properties.region))
            .overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .apiCallTimeout(properties.apiCallTimeout)
                    .apiCallAttemptTimeout(properties.apiCallAttemptTimeout)
                    .build(),
            )
            .build()
    }

    // 서명은 네트워크 호출 없이 로컬에서 계산되므로 제한 시간 설정이 없다.
    @Bean
    fun s3Presigner(properties: S3ObjectStorageProperties): S3Presigner {
        return S3Presigner.builder()
            .region(Region.of(properties.region))
            .build()
    }
}
