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
        val options = FirebaseOptions.builder()
            .setCredentials(loadFcmCredentials(properties.serviceAccountJson))
            .setProjectId(properties.projectId)
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
        properties: FcmWebPushProperties,
    ): WebPushSender = FcmWebPushSender(
        gateway = gateway,
        invalidRegistrationRemover = invalidWebPushRegistrationRemover,
        actionBaseUrl = properties.actionBaseUrl,
    )
}

@ConfigurationProperties("notification.web-push.fcm")
internal data class FcmWebPushProperties(
    val projectId: String,
    val actionBaseUrl: String,
    val serviceAccountJson: String? = null,
)

private const val FIREBASE_APP_NAME = "moimyeon-web-push"
