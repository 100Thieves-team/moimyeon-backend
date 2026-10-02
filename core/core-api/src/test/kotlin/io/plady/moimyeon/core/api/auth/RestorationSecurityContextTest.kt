package io.plady.moimyeon.core.api.auth

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.security.auth.AuthCookieFactory
import io.plady.moimyeon.security.auth.RestoreTokenProvider
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.RefreshTokenRepository
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
import jakarta.servlet.Filter
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime
import java.util.UUID

// 복구 요청은 실제 보안 필터 체인을 거친다. 공개 경로·액세스 토큰 해석 제외 설정이 빠지면 흐름 전체가 401 이 된다.
class RestorationSecurityContextTest(
    private val webApplicationContext: WebApplicationContext,
    @Qualifier("springSecurityFilterChain") private val securityFilterChain: Filter,
    private val restoreTokenProvider: RestoreTokenProvider,
    private val authCookieFactory: AuthCookieFactory,
    private val memberRepository: MemberRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
) : ContextTest() {
    private lateinit var mockMvc: MockMvc
    private val memberId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val builder = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        builder.addFilters<DefaultMockMvcBuilder>(securityFilterChain)
        mockMvc = builder.build()
    }

    @AfterEach
    fun cleanUp() {
        refreshTokenRepository.deleteAll(refreshTokenRepository.findAll().filter { it.memberId == memberId })
        if (memberRepository.existsById(memberId)) memberRepository.deleteById(memberId)
    }

    @Test
    fun `만료된 액세스 쿠키가 함께 와도 복구 확인 토큰으로 탈퇴 계정을 복구한다`() {
        persistWithdrawnMember()

        val response = mockMvc.perform(
            post("/v1/auth/restoration")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(
                    Cookie(authCookieFactory.accessTokenName, "expired-or-garbage-access-token"),
                    Cookie(authCookieFactory.restoreTokenName, restoreTokenProvider.issue(memberId)),
                ),
        ).andReturn().response

        assertThat(response.status).isEqualTo(200)
        assertThat(memberRepository.findById(memberId).orElseThrow().isDeleted()).isFalse()
    }

    @Test
    fun `위조된 복구 확인 토큰은 인증 필터가 아니라 복구 API 가 E1105 로 거절한다`() {
        val response = mockMvc.perform(
            post("/v1/auth/restoration")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(Cookie(authCookieFactory.restoreTokenName, "forged-restore-token")),
        ).andReturn().response

        assertThat(response.status).isEqualTo(401)
        assertThat(response.contentAsString).contains("E1105")
    }

    private fun persistWithdrawnMember() {
        val suffix = memberId.toString().take(8)
        memberRepository.saveAndFlush(
            MemberEntity(
                id = memberId,
                email = "restore-$suffix@example.com",
                nickname = "복구$suffix",
                status = MemberStatus.ACTIVE,
                lastLoginAt = LocalDateTime.now().minusDays(1),
                socialAccounts = listOf(
                    SocialAccountEntity(SocialLoginProvider.GOOGLE, "restore-$suffix", "restore-$suffix@example.com"),
                ),
            ).apply { delete(LocalDateTime.now().minusMinutes(1)) },
        )
    }
}
