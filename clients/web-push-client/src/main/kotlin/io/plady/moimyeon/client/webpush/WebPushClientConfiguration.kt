package io.plady.moimyeon.client.webpush

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import io.plady.moimyeon.worker.notification.delivery.InvalidWebPushRegistrationRemover
import io.plady.moimyeon.worker.notification.delivery.WebPushSender
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("local-dev", "dev", "staging", "live")
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FcmWebPushProperties::class)
internal class WebPushClientConfiguration {
    @Bean(destroyMethod = "delete")
    fun webPushFirebaseApp(properties: FcmWebPushProperties): FirebaseApp {
        // 시간 제한을 정하지 않으면 SDK 기본값(무제한·전송 계층 기본)을 따라, 느린 FCM 응답이 Worker 소비 주기를
        // 붙잡아 하트비트 기반 상태 검사(5분)를 넘길 수 있다(MOI-594).
        val options = FirebaseOptions.builder()
            .setCredentials(loadFcmCredentials(properties.serviceAccountJson))
            .setProjectId(properties.projectId)
            .setConnectTimeout(FCM_TIMEOUT_MILLIS)
            .setReadTimeout(FCM_TIMEOUT_MILLIS)
            .build()
        return FirebaseApp.initializeApp(options, FIREBASE_APP_NAME)
    }

    @Bean
    fun webPushFirebaseMessaging(webPushFirebaseApp: FirebaseApp): FirebaseMessaging = FirebaseMessaging.getInstance(webPushFirebaseApp)

    @Bean
    fun fcmGateway(webPushFirebaseMessaging: FirebaseMessaging): FcmGateway = FirebaseAdminFcmGateway(webPushFirebaseMessaging)

    @Bean
    fun webPushSender(
        gateway: FcmGateway,
        invalidWebPushRegistrationRemover: InvalidWebPushRegistrationRemover,
    ): WebPushSender = FcmWebPushSender(
        gateway = gateway,
        invalidRegistrationRemover = invalidWebPushRegistrationRemover,
    )
}

@ConfigurationProperties("notification.web-push.fcm")
internal data class FcmWebPushProperties(
    val projectId: String,
    val serviceAccountJson: String? = null,
)

private const val FIREBASE_APP_NAME = "moimyeon-web-push"
private const val FCM_TIMEOUT_MILLIS = 10_000
