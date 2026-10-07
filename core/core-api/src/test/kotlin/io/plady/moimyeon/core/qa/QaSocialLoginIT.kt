package io.plady.moimyeon.core.qa

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.MemberRegistrationManager
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.security.auth.AuthCookieFactory
import io.plady.moimyeon.security.auth.SocialLanding
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.util.UUID

class QaSocialLoginIT(
    private val qaSocialLogin: QaSocialLogin,
    private val qaMemberCreator: QaMemberCreator,
    private val qaMemberEraser: QaMemberEraser,
    private val memberRepository: MemberRepository,
    private val memberRegistrationManager: MemberRegistrationManager,
    private val authCookieFactory: AuthCookieFactory,
    private val jdbcTemplate: JdbcTemplate,
) : ContextTest() {
    private val qaMemberIds = mutableListOf<UUID>()
    private val otherMemberIds = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        qaMemberIds.forEach { qaMemberEraser.erase(it) }
        otherMemberIds.forEach { id ->
            listOf(
                "delete from terms_agreement where member_id = ?",
                "delete from member_profile where member_id = ?",
                "delete from social_account where member_id = ?",
                "delete from member where id = ?",
            ).forEach { jdbcTemplate.update(it, bytes(id)) }
        }
    }

    @Test
    fun `처음 보는 QA 소셜 계정이면 가입하고 로그인 쿠키를 만든다`() {
        val landing = qaSocialLogin.signUp().also { qaMemberIds += it.memberId }

        assertThat(landing.outcome).isEqualTo(SocialLanding.Outcome.LOGGED_IN)
        assertThat(landing.cookies.map { it.name }).containsExactly(authCookieFactory.accessTokenName, authCookieFactory.refreshTokenName)
        val entity = memberRepository.findWithSocialAccountsByIdAndDeletedAtIsNull(landing.memberId)!!
        assertThat(entity.socialAccounts().single().providerId).startsWith(QaMemberCreator.PROVIDER_ID_PREFIX)
    }

    @Test
    fun `QA 회원이면 그 소셜 계정으로 로그인한다`() {
        val member = qaMemberCreator.create().also { qaMemberIds += it.id }

        val landing = qaSocialLogin.login(member.id)

        assertThat(landing.memberId).isEqualTo(member.id)
        assertThat(landing.outcome).isEqualTo(SocialLanding.Outcome.LOGGED_IN)
    }

    @Test
    fun `탈퇴한 QA 회원이면 로그인 대신 복구 확인 쿠키만 만든다`() {
        val member = qaMemberCreator.create().also { qaMemberIds += it.id }
        memberRepository.findById(member.id).orElseThrow().let {
            it.delete(LocalDateTime.now())
            memberRepository.saveAndFlush(it)
        }

        val landing = qaSocialLogin.login(member.id)

        assertThat(landing.memberId).isEqualTo(member.id)
        assertThat(landing.outcome).isEqualTo(SocialLanding.Outcome.RESTORE_REQUIRED)
        assertThat(landing.cookies.map { it.name }).containsExactly(authCookieFactory.restoreTokenName)
    }

    @Test
    fun `QA 회원이 아니면 E2201 로 거절한다`() {
        val memberId = memberRegistrationManager.register(SocialLoginProvider.GOOGLE, "sub-${UUID.randomUUID()}", Email("member@example.com"))
            .also { otherMemberIds += it }

        assertThatThrownBy { qaSocialLogin.login(memberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.QA_DATA_ONLY)
            }
    }

    private fun bytes(uuid: UUID): ByteArray = ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array()
}
