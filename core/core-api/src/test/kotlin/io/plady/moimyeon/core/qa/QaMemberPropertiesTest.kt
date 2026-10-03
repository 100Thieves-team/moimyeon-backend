package io.plady.moimyeon.core.qa

import io.plady.moimyeon.core.domain.member.Email
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class QaMemberPropertiesTest {
    @Test
    fun `기본 주소는 기존 QA 회원 도메인이다`() {
        val email = QaMemberProperties().emailOf("abc")

        assertThat(email).isEqualTo("qa-abc@${QaMemberCreator.EMAIL_DOMAIN}")
    }

    @Test
    fun `받을 수 있는 메일함의 플러스 주소로 바꿀 수 있다`() {
        val key = "0b7c4f3e-2a1d-4c55-9f2e-8d1a6b3c9e01"
        val email = QaMemberProperties(emailTemplate = "moimyeon.qa+qa-{key}@gmail.com").emailOf(key)

        assertThat(email).isEqualTo("moimyeon.qa+qa-$key@gmail.com")
        assertThat(Email(email).value).isEqualTo(email)
    }

    @Test
    fun `키 자리가 없는 템플릿은 거절한다`() {
        assertThatThrownBy { QaMemberProperties(emailTemplate = "qa@gmail.com") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
