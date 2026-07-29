package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.enums.TermsStatus
import io.plady.moimyeon.core.enums.TermsType
import io.plady.moimyeon.storage.db.CoreDbContextTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class TermsAgreementRepositoryIT(
    val termsAgreementRepository: TermsAgreementRepository,
    val termsRepository: TermsRepository,
    val memberRepository: MemberRepository,
) : CoreDbContextTest() {
    private val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

    private fun persistMember(providerId: String): UUID {
        val member = MemberEntity(
            id = UUID.randomUUID(),
            email = "user@example.com",
            nickname = "nick-$providerId",
            status = MemberStatus.ACTIVE,
            lastLoginAt = now,
            withdrawnAt = null,
            socialAccounts = mutableListOf(
                SocialAccountEntity(SocialLoginProvider.GOOGLE, providerId, "user@example.com"),
            ),
        )
        return memberRepository.saveAndFlush(member).id
    }

    private fun persistTerms(version: String): UUID {
        val terms = TermsEntity(
            id = UUID.randomUUID(),
            type = TermsType.SERVICE,
            version = version,
            title = "이용약관",
            content = "본문",
            required = true,
            effectiveFrom = now,
            status = TermsStatus.ACTIVE,
        )
        return termsRepository.saveAndFlush(terms).id
    }

    @Test
    fun `동의 이력을 저장하고 회원별로 조회한다`() {
        // given
        val memberId = persistMember("google-sub-1")
        val termsId = persistTerms("test-1.0")
        termsAgreementRepository.saveAndFlush(
            TermsAgreementEntity(id = UUID.randomUUID(), memberId = memberId, termsId = termsId, agreedAt = now),
        )

        // when
        val found = termsAgreementRepository.findByMemberId(memberId)

        // then
        assertThat(found).hasSize(1)
        assertThat(found[0].termsId).isEqualTo(termsId)
        assertThat(termsAgreementRepository.existsByMemberIdAndTermsId(memberId, termsId)).isTrue()
    }

    @Test
    fun `같은 (member, terms) 쌍은 유니크 제약으로 중복 기록되지 않는다`() {
        // given
        val memberId = persistMember("google-sub-2")
        val termsId = persistTerms("test-1.1")
        termsAgreementRepository.saveAndFlush(
            TermsAgreementEntity(id = UUID.randomUUID(), memberId = memberId, termsId = termsId, agreedAt = now),
        )

        // when & then — 재동의는 새 약관 버전(termsId)으로만 가능하다(append-only)
        assertThatThrownBy {
            termsAgreementRepository.saveAndFlush(
                TermsAgreementEntity(id = UUID.randomUUID(), memberId = memberId, termsId = termsId, agreedAt = now),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
