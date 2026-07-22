package io.plady.moimyeon.core.domain

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.SocialAccountEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class MemberFinderTest {
    private val memberRepository = mockk<MemberRepository>()
    private val memberFinder = MemberFinder(memberRepository)

    private val provider = SocialLoginProvider.GOOGLE
    private val now = LocalDateTime.of(2026, 1, 1, 0, 0)

    @Test
    fun `소셜 계정으로 조회되는 회원이 없으면 null 을 반환한다`() {
        // given
        every {
            memberRepository.findBySocialAccountsProviderAndSocialAccountsProviderId(provider, "no-such-sub")
        } returns null

        // when
        val found = memberFinder.findBySocialAccount(provider, "no-such-sub")

        // then
        assertThat(found).isNull()
    }

    @Test
    fun `엔티티를 도메인 Member 로 변환해 반환한다 (Service 로 엔티티가 새지 않는다)`() {
        // given
        val id = UUID.randomUUID()
        val entity = MemberEntity(
            id = id,
            email = "user@example.com",
            status = MemberStatus.ACTIVE,
            lastLoginAt = now,
            withdrawnAt = null,
            socialAccounts = mutableListOf(SocialAccountEntity(provider, "sub-1", "social@example.com")),
        )
        every {
            memberRepository.findBySocialAccountsProviderAndSocialAccountsProviderId(provider, "sub-1")
        } returns entity

        // when
        val member = memberFinder.findBySocialAccount(provider, "sub-1")

        // then
        assertThat(member).isNotNull
        assertThat(member!!.id).isEqualTo(id)
        assertThat(member.email).isEqualTo(Email("user@example.com"))
        assertThat(member.status).isEqualTo(MemberStatus.ACTIVE)
        assertThat(member.socialAccounts).hasSize(1)
        assertThat(member.socialAccounts.first().provider).isEqualTo(provider)
        assertThat(member.socialAccounts.first().providerId).isEqualTo("sub-1")
        assertThat(member.socialAccounts.first().linkedEmail).isEqualTo(Email("social@example.com"))
    }
}
