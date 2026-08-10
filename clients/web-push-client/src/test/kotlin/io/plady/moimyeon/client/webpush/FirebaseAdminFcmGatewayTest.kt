package io.plady.moimyeon.client.webpush

import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.SendResponse
import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.worker.notification.delivery.PermanentWebPushDeliveryException
import io.plady.moimyeon.worker.notification.delivery.RetryableWebPushDeliveryException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class FirebaseAdminFcmGatewayTest {
    private val firebaseMessaging = mockk<FirebaseMessaging>()
    private val gateway = FirebaseAdminFcmGateway(firebaseMessaging)

    @Test
    fun `FCM의 개별 응답을 등록 식별자 순서대로 분류한다`() {
        every { firebaseMessaging.sendEachForMulticast(any()) } returns batch(
            success(),
            failure(MessagingErrorCode.UNREGISTERED),
            failure(MessagingErrorCode.UNAVAILABLE),
            failure(MessagingErrorCode.INVALID_ARGUMENT),
        )

        val results = gateway.send(request("a", "b", "c", "d"))

        assertThat(results).containsExactly(
            FcmSendResult.success("a"),
            FcmSendResult.unregistered("b"),
            FcmSendResult.retryableFailure("c"),
            FcmSendResult.permanentFailure("d"),
        )
    }

    @Test
    fun `FCM 전체 호출의 일시 오류를 재시도 오류로 변환한다`() {
        every { firebaseMessaging.sendEachForMulticast(any()) } throws
            messagingException(MessagingErrorCode.QUOTA_EXCEEDED)

        assertThatThrownBy { gateway.send(request("a")) }
            .isInstanceOf(RetryableWebPushDeliveryException::class.java)
    }

    @Test
    fun `FCM 전체 호출의 영구 오류를 영구 전송 오류로 변환한다`() {
        every { firebaseMessaging.sendEachForMulticast(any()) } throws
            messagingException(MessagingErrorCode.SENDER_ID_MISMATCH)

        assertThatThrownBy { gateway.send(request("a")) }
            .isInstanceOf(PermanentWebPushDeliveryException::class.java)
    }

    private fun request(vararg registrations: String) = FcmMulticastRequest(
        registrations = registrations.toList(),
        title = "title",
        body = "body",
        actionUrl = "https://moimyeon.com/rooms/room-1",
        data = mapOf("eventId" to "event-id"),
    )

    private fun batch(vararg responses: SendResponse) = mockk<BatchResponse> {
        every { getResponses() } returns responses.toList()
    }

    private fun success() = mockk<SendResponse> {
        every { isSuccessful } returns true
    }

    private fun failure(errorCode: MessagingErrorCode) = mockk<SendResponse> {
        every { isSuccessful } returns false
        every { exception } returns messagingException(errorCode)
    }

    private fun messagingException(errorCode: MessagingErrorCode) = mockk<FirebaseMessagingException> {
        every { messagingErrorCode } returns errorCode
    }
}
