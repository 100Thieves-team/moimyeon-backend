package io.plady.moimyeon.core.qa

import io.plady.moimyeon.storage.db.core.qa.QaEmailPattern
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.core.env.StandardEnvironment
import java.util.UUID

class QaMemberPropertiesTest {
    @Test
    fun `기본 주소는 기존 QA 회원 도메인이다`() {
        val properties = QaMemberProperties()

        assertThat(properties.emailOf("abc")).isEqualTo("qa-abc@${QaMemberCreator.EMAIL_DOMAIN}")
        assertThat(properties.emailPatterns()).containsExactly(QaEmailPattern("qa-", "@${QaMemberCreator.EMAIL_DOMAIN}"), QaEmailPattern("", "@${QaMemberCreator.EMAIL_DOMAIN}"))
    }

    @Test
    fun `받을 수 있는 메일함의 플러스 주소로 바꾸면 예전 도메인 회원도 함께 QA 회원으로 본다`() {
        val properties = QaMemberProperties(emailTemplate = "moimyeon.qa+qa-{key}@gmail.com")
        val memberKey = UUID.randomUUID().toString()

        assertThat(properties.emailOf(memberKey)).isEqualTo("moimyeon.qa+qa-$memberKey@gmail.com")
        assertThat(properties.emailPatterns()).containsExactly(QaEmailPattern("moimyeon.qa+qa-", "@gmail.com"), QaEmailPattern("", "@${QaMemberCreator.EMAIL_DOMAIN}"))
    }

    @Test
    fun `키 자리가 없거나 키를 넣은 주소가 이메일 형식이 아니면 시작할 때 거절한다`() {
        listOf("qa@gmail.com", "{key}-{key}@gmail.com", "invalid-{key}").forEach { template ->
            assertThatThrownBy { QaMemberProperties(emailTemplate = template) }
                .describedAs(template)
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `설정 파일의 기본값 자리표시를 풀어도 키 자리는 남는다`() {
        val resolved = StandardEnvironment().resolvePlaceholders("\${QA_MEMBER_EMAIL_TEMPLATE_UNSET:100dodukteam+qa-{key}@gmail.com}")

        assertThat(resolved).isEqualTo("100dodukteam+qa-{key}@gmail.com")
        assertThat(QaMemberProperties(emailTemplate = resolved).emailPatterns().first()).isEqualTo(QaEmailPattern("100dodukteam+qa-", "@gmail.com"))
    }
}
