package io.plady.moimyeon.core.domain.analytics

import io.plady.moimyeon.support.logging.GrowthEventEntry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class AnalyticsIdGeneratorTest {
    private val idGenerator = AnalyticsIdGenerator(AnalyticsProperties(idHmacKey = KEY))

    @Test
    fun `같은 회원은 항상 같은 analyticsId를 받는다`() {
        assertThat(idGenerator.generate(MEMBER_A)).isEqualTo(idGenerator.generate(MEMBER_A))
    }

    @Test
    fun `다른 회원이나 다른 키는 다른 analyticsId를 받는다`() {
        val otherKeyGenerator = AnalyticsIdGenerator(AnalyticsProperties(idHmacKey = "other-analytics-key-with-at-least-32-bytes"))

        assertThat(idGenerator.generate(MEMBER_A)).isNotEqualTo(idGenerator.generate(MEMBER_B))
        assertThat(idGenerator.generate(MEMBER_A)).isNotEqualTo(otherKeyGenerator.generate(MEMBER_A))
    }

    @Test
    fun `analyticsId는 그로스 사건이 받는 32자리 소문자 16진수다`() {
        val analyticsId = idGenerator.generate(MEMBER_A)

        assertThat(analyticsId).matches("[0-9a-f]{32}")
        assertThatCode { GrowthEventEntry("member.signed_up", UUID.randomUUID(), analyticsId) }.doesNotThrowAnyException()
    }

    @Test
    fun `알려진 키와 회원 ID는 고정 기대값을 만든다`() {
        // HMAC-SHA-256(키, UUID 16바이트)의 앞 16바이트. 다른 언어로 같은 값을 만들 때의 기준값이다.
        assertThat(idGenerator.generate(MEMBER_A)).isEqualTo("b0a6a2972ac9b8ad2920be6f45b8fd5a")
    }

    @Test
    fun `키가 설정되지 않으면 analyticsId를 만들지 않는다`() {
        assertThat(AnalyticsIdGenerator(AnalyticsProperties(idHmacKey = "")).generate(MEMBER_A)).isNull()
        assertThat(AnalyticsIdGenerator(AnalyticsProperties()).generate(MEMBER_A)).isNull()
    }

    @Test
    fun `키가 32바이트보다 짧으면 설정을 거부한다`() {
        assertThatThrownBy { AnalyticsProperties(idHmacKey = "short-key") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `설정 객체의 문자열 표현에 키 원문이 나오지 않는다`() {
        assertThat(AnalyticsProperties(idHmacKey = KEY).toString()).doesNotContain(KEY)
    }

    companion object {
        private const val KEY = "test-analytics-key-with-at-least-32-bytes" // gate:allow-secret
        private val MEMBER_A = UUID.fromString("019daf00-0000-7000-8000-000000000001")
        private val MEMBER_B = UUID.fromString("019daf00-0000-7000-8000-000000000002")
    }
}
