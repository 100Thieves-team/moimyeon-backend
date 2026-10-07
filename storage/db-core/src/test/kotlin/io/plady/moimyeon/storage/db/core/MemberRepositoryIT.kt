package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.CoreDbContextTest
import jakarta.persistence.EntityManager
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
    val entityManager: EntityManager,
) : CoreDbContextTest() {
    private val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

    private fun newMember(
        providerId: String,
        memberEmail: String = "user@example.com", // 회원 계정 이메일 (가입 후 변경될 수 있음)
        socialEmail: String = memberEmail, // 공급자가 알려준 소셜 계정 이메일 (member.email 과 별개의 값)
        nickname: String = "nick-$providerId",
    ) = MemberEntity(
        id = UUID.randomUUID(),
        email = memberEmail,
        nickname = nickname,
        status = MemberStatus.ACTIVE,
        lastLoginAt = now,
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
        assertThat(found.isDeleted()).isFalse()
        assertThat(found.createdAt).isNotNull()
        assertThat(found.socialAccounts()).hasSize(1)
        assertThat(found.socialAccounts().first().providerId).isEqualTo("google-sub-1")
    }

    @Test
    fun `기본 회원 조회는 소셜 계정을 지연 로딩한다`() {
        val memberId = memberRepository.saveAndFlush(newMember(providerId = "google-sub-lazy")).id
        entityManager.clear()

        val found = memberRepository.findByIdAndDeletedAtIsNull(memberId)!!

        assertThat(entityManager.entityManagerFactory.persistenceUnitUtil.isLoaded(found, "socialAccounts")).isFalse()
    }

    @Test
    fun `withSocialAccounts 단건 조회는 영속성 컨텍스트 밖에서도 소셜 계정을 제공한다`() {
        val memberId = memberRepository.saveAndFlush(newMember(providerId = "google-sub-fetch-one")).id
        entityManager.clear()

        val found = memberRepository.findWithSocialAccountsByIdAndDeletedAtIsNull(memberId)!!
        entityManager.clear()

        assertThat(found.socialAccounts().map { it.providerId }).containsExactly("google-sub-fetch-one")
    }

    @Test
    fun `withSocialAccounts 다건 조회는 영속성 컨텍스트 밖에서도 회원별 소셜 계정을 제공한다`() {
        val members = memberRepository.saveAllAndFlush(
            listOf(
                newMember(providerId = "google-sub-fetch-many-1"),
                newMember(providerId = "google-sub-fetch-many-2"),
            ),
        )
        entityManager.clear()

        val found = memberRepository.findAllWithSocialAccountsByIdInAndDeletedAtIsNull(members.map { it.id })
        entityManager.clear()

        assertThat(found.map { it.id }).containsExactlyInAnyOrderElementsOf(members.map { it.id })
        assertThat(found.flatMap { it.socialAccounts() }.map { it.providerId })
            .containsExactlyInAnyOrder("google-sub-fetch-many-1", "google-sub-fetch-many-2")
    }

    @Test
    fun `provider 와 providerId 로 살아있는 회원을 찾고, 탈퇴 회원은 걸러진다`() {
        // given
        val member = memberRepository.saveAndFlush(newMember(providerId = "google-sub-2"))

        // when
        val found = memberRepository.findBySocialAccountsProviderAndSocialAccountsProviderIdAndDeletedAtIsNull(
            SocialLoginProvider.GOOGLE,
            "google-sub-2",
        )

        // then
        assertThat(found?.id).isEqualTo(member.id)

        // 탈퇴(소프트 삭제)하면 같은 신원으로 조회되지 않는다
        found!!.delete(now)
        memberRepository.saveAndFlush(found)
        assertThat(
            memberRepository.findBySocialAccountsProviderAndSocialAccountsProviderIdAndDeletedAtIsNull(
                SocialLoginProvider.GOOGLE,
                "google-sub-2",
            ),
        ).isNull()

        // 재가입 차단 판정용으로는 여전히 조회된다
        assertThat(
            memberRepository.existsBySocialAccountsProviderAndSocialAccountsProviderIdAndDeletedAtIsNotNull(
                SocialLoginProvider.GOOGLE,
                "google-sub-2",
            ),
        ).isTrue()
    }

    @Test
    fun `같은 provider 와 providerId 는 유니크 제약으로 중복 저장되지 않는다`() {
        // given
        memberRepository.saveAndFlush(newMember(providerId = "google-sub-3"))

        // when & then — 닉네임은 달리해 (provider, providerId) 유니크 위반만 검증한다
        assertThatThrownBy {
            memberRepository.saveAndFlush(newMember(providerId = "google-sub-3", nickname = "다른 닉네임 01"))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `닉네임은 유니크 제약으로 중복 저장되지 않는다`() {
        // given
        memberRepository.saveAndFlush(newMember(providerId = "google-sub-6", nickname = "중복 닉네임 01"))

        // when & then
        assertThatThrownBy {
            memberRepository.saveAndFlush(newMember(providerId = "google-sub-7", nickname = "중복 닉네임 01"))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `닉네임 존재 여부와 자신 제외 존재 여부를 확인한다`() {
        // given
        val mine = memberRepository.saveAndFlush(newMember(providerId = "google-sub-8", nickname = "내 닉네임 01")).id

        // when & then — 자기 닉네임은 중복이 아니고, 남이 보면 중복이다
        assertThat(memberRepository.existsByNickname("내 닉네임 01")).isTrue()
        assertThat(memberRepository.existsByNickname("없는 닉네임")).isFalse()
        assertThat(memberRepository.existsByNicknameAndIdNot("내 닉네임 01", mine)).isFalse()
        assertThat(memberRepository.existsByNicknameAndIdNot("내 닉네임 01", UUID.randomUUID())).isTrue()
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
