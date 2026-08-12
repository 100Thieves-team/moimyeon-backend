package io.plady.moimyeon.core.domain.trust

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class TrustServiceTest {
    private val trustFinder = mockk<TrustFinder>()
    private val trustService = TrustService(trustFinder)
    private val memberId = UUID.randomUUID()

    @Test
    fun `공개 신뢰 조회를 Finder 에 위임하고 결과를 반환한다`() {
        val trust = PublicTrust.empty()
        every { trustFinder.getPublicTrust(memberId) } returns trust

        val result = trustService.getPublicTrust(memberId)

        assertThat(result).isSameAs(trust)
        verify(exactly = 1) { trustFinder.getPublicTrust(memberId) }
    }

    @Test
    fun `Finder 의 조회 예외를 그대로 전파한다`() {
        val exception = IllegalStateException("신뢰 지표 조회 실패")
        every { trustFinder.getPublicTrust(memberId) } throws exception

        assertThatThrownBy { trustService.getPublicTrust(memberId) }
            .isSameAs(exception)
    }
}
