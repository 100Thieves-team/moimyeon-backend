package io.plady.moimyeon.core.domain.session

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionCredentialTest {
    @Test
    fun `세션 크리덴셜을 발급하면 원문과 저장용 해시가 다르다`() {
        val credential = SessionCredential.issue()

        assertThat(credential.value).isNotBlank()
        assertThat(credential.hash()).hasSize(64)
        assertThat(credential.hash()).isNotEqualTo(credential.value)
    }

    @Test
    fun `같은 세션 크리덴셜 원문은 같은 해시를 만든다`() {
        val first = SessionCredential.from("same-credential")
        val second = SessionCredential.from("same-credential")

        assertThat(first.hash()).isEqualTo(second.hash())
    }
}
