package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.CoreDbContextTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class MemberRepositoryIT(
    val memberRepository: MemberRepository,
) : CoreDbContextTest() {
    private val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

    private fun newMember(
        providerId: String,
        memberEmail: String = "user@example.com", // 회원 계정 이메일 (가입 후 변경될 수 있음)
        socialEmail: String = memberEmail, // 공급자가 알려준 소셜 계정 이메일 (member.email 과 별개의 값)
    ) = MemberEntity(
        id = UUID.randomUUID(),
        email = memberEmail,
        status = MemberStatus.ACTIVE,
        lastLoginAt = now,
        withdrawnAt = null,
        socialAccounts = mutableListOf(
            SocialAccountEntity(SocialLoginProvider.GOOGLE, providerId, socialEmail),
        ),
    )

    @Test
    fun `회원과 소셜 계정이 함께 저장되고 UUID 로 조회된다`() {
        // given
        val member = newMember(providerId = "google-sub-1")

        // when
        val saved = memberRepository.saveAndFlush(member)
        val found = memberRepository.findById(saved.id).get()

        // then
        assertThat(found.id).isEqualTo(member.id)
        assertThat(found.status).isEqualTo(MemberStatus.ACTIVE)
        assertThat(found.withdrawnAt).isNull()
        assertThat(found.createdAt).isNotNull()
        assertThat(found.socialAccounts).hasSize(1)
        assertThat(found.socialAccounts.first().providerId).isEqualTo("google-sub-1")
    }

    @Test
    fun `provider 와 providerId 로 기존 회원을 찾는다`() {
        // given
        val member = memberRepository.saveAndFlush(newMember(providerId = "google-sub-2"))

        // when
        val found = memberRepository.findBySocialAccountsProviderAndSocialAccountsProviderId(
            SocialLoginProvider.GOOGLE,
            "google-sub-2",
        )

        // then
        assertThat(found?.id).isEqualTo(member.id)
    }

    @Test
    fun `같은 provider 와 providerId 는 유니크 제약으로 중복 저장되지 않는다`() {
        // given
        memberRepository.saveAndFlush(newMember(providerId = "google-sub-3"))

        // when & then
        assertThatThrownBy {
            memberRepository.saveAndFlush(newMember(providerId = "google-sub-3"))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `회원 식별은 이메일이 아니라 (provider, providerId) 로 한다`() {
        // 이메일은 식별 키가 아니다.
        // member.email(회원 계정 이메일)과 socialAccount.linkedEmail(공급자가 알려준 이메일)은 서로 다른 값이며, 어느 쪽도 유니크하지 않다.
        // 따라서 계정 이메일이 겹쳐도 소셜 신원(provider, providerId)이 다르면 서로 다른 회원이다.

        // given: 계정 이메일은 같지만, 소셜 신원(providerId)과 소셜 이메일이 서로 다른 두 회원
        val first = memberRepository.saveAndFlush(
            newMember(providerId = "google-sub-4", memberEmail = "same@example.com", socialEmail = "a@gmail.com"),
        )

        // when
        val second = memberRepository.saveAndFlush(
            newMember(providerId = "google-sub-5", memberEmail = "same@example.com", socialEmail = "b@gmail.com"),
        )

        // then: 계정 이메일이 같아도 (provider, providerId)가 다르므로 별도 회원으로 공존한다
        assertThat(second.id).isNotEqualTo(first.id)
        assertThat(memberRepository.findById(first.id)).isPresent()
        assertThat(memberRepository.findById(second.id)).isPresent()
    }
}
