package io.plady.moimyeon.core.qa

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.storage.db.core.MemberProfileRepository
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.TermsAgreementRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.ByteBuffer
import java.util.UUID

class QaMemberCreatorIT(
    private val qaMemberCreator: QaMemberCreator,
    private val memberRepository: MemberRepository,
    private val memberProfileRepository: MemberProfileRepository,
    private val termsAgreementRepository: TermsAgreementRepository,
    private val jdbcTemplate: JdbcTemplate,
) : ContextTest() {
    private val createdIds = mutableListOf<UUID>()

    @AfterEach
    fun cleanUp() {
        createdIds.forEach { id ->
            listOf(
                "delete from terms_agreement where member_id = ?",
                "delete from member_profile where member_id = ?",
                "delete from social_account where member_id = ?",
                "delete from member where id = ?",
            ).forEach { jdbcTemplate.update(it, bytes(id)) }
        }
    }

    @Test
    fun `실제 가입 경로로 테스트 회원을 만들고 약관 동의와 빈 프로필까지 갖춘다`() {
        val member = qaMemberCreator.create().also { createdIds += it.id }

        val entity = memberRepository.findWithSocialAccountsByIdAndDeletedAtIsNull(member.id)!!
        assertThat(entity.status).isEqualTo(MemberStatus.ACTIVE)
        assertThat(entity.email).endsWith("@${QaMemberCreator.EMAIL_DOMAIN}")
        assertThat(entity.socialAccounts().single().providerId).startsWith(QaMemberCreator.PROVIDER_ID_PREFIX)
        assertThat(member.nickname).isEqualTo(entity.nickname)
        assertThat(memberProfileRepository.findAll().filter { it.memberId == member.id }).hasSize(1)
        assertThat(termsAgreementRepository.findAll().filter { it.memberId == member.id }).isNotEmpty()
    }

    @Test
    fun `호출할 때마다 서로 다른 회원을 만든다`() {
        val first = qaMemberCreator.create().also { createdIds += it.id }
        val second = qaMemberCreator.create().also { createdIds += it.id }

        assertThat(first.id).isNotEqualTo(second.id)
        assertThat(first.email).isNotEqualTo(second.email)
    }

    private fun bytes(uuid: UUID): ByteArray = ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array()
}
