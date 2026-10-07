package io.plady.moimyeon.core.domain.session

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class SessionServiceTest {
    private val sessionAuthenticator = mockk<SessionAuthenticator>()
    private val sessionManager = mockk<SessionManager>()
    private val sessionService = SessionService(sessionAuthenticator, sessionManager)

    private val memberId = UUID.randomUUID()

    @Test
    fun `회원의 세션을 연다`() {
        val session = Session(
            credential = SessionCredential.from("issued-credential"),
            expiresAt = LocalDateTime.of(2026, 8, 15, 12, 0),
        )
        every { sessionManager.open(memberId, any()) } returns session

        val result = sessionService.open(memberId)

        assertThat(result).isSameAs(session)
        verify(exactly = 1) { sessionManager.open(memberId, any()) }
    }

    @Test
    fun `세션 크리덴셜로 회원을 인증한다`() {
        every { sessionAuthenticator.authenticate(SessionCredential.from("raw"), any()) } returns memberId

        val result = sessionService.authenticate("raw")

        assertThat(result).isEqualTo(memberId)
    }

    @Test
    fun `로그아웃하면 세션을 종료한다`() {
        every { sessionManager.close(SessionCredential.from("raw"), any()) } just Runs

        sessionService.logout("raw")

        verify(exactly = 1) { sessionManager.close(SessionCredential.from("raw"), any()) }
    }
}
