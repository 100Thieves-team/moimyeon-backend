package io.plady.moimyeon.core.domain.notification

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class WebPushRegistrationTest {
    @Test
    fun `브라우저가 발급받은 등록 식별자를 보존한다`() {
        val registration = WebPushRegistration("fcm-registration-id")

        assertThat(registration.value).isEqualTo("fcm-registration-id")
    }

    @Test
    fun `비어 있는 등록 식별자는 거절한다`() {
        assertThatThrownBy { WebPushRegistration("  ") }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.INVALID_WEB_PUSH_REGISTRATION)
            }
    }
}
