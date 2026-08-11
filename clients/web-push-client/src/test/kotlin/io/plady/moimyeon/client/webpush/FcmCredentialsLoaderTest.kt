package io.plady.moimyeon.client.webpush

import com.google.auth.oauth2.GoogleCredentials
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FcmCredentialsLoaderTest {
    @Test
    fun `서비스 계정 JSON이 있으면 그 값으로 FCM 자격 증명을 만든다`() {
        val expected = mockk<GoogleCredentials>()
        var parsedJson: String? = null
        var applicationDefaultRequested = false

        val credentials = loadFcmCredentials(
            serviceAccountJson = "{\"type\":\"service_account\"}",
            applicationDefault = {
                applicationDefaultRequested = true
                mockk()
            },
            fromStream = {
                parsedJson = it.bufferedReader().readText()
                expected
            },
        )

        assertThat(credentials).isSameAs(expected)
        assertThat(parsedJson).isEqualTo("{\"type\":\"service_account\"}")
        assertThat(applicationDefaultRequested).isFalse()
    }

    @Test
    fun `서비스 계정 JSON이 없으면 Application Default Credentials를 사용한다`() {
        val expected = mockk<GoogleCredentials>()
        var streamParserCalled = false

        val credentials = loadFcmCredentials(
            serviceAccountJson = " ",
            applicationDefault = { expected },
            fromStream = {
                streamParserCalled = true
                mockk()
            },
        )

        assertThat(credentials).isSameAs(expected)
        assertThat(streamParserCalled).isFalse()
    }
}
