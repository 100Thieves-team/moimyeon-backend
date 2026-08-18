package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.member.Nickname
import io.plady.moimyeon.core.domain.member.SocialAuthService
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.MemberProfileRepository
import io.plady.moimyeon.storage.db.core.TermsAgreementRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.test.annotation.DirtiesContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID

// 이슈 Verification: 가입(약관 자동 동의 + 빈 프로필 생성) → 소개 저장 흐름 재현
@Transactional
class ProfileServiceIT(
    private val socialAuthService: SocialAuthService,
    private val memberService: MemberService,
    private val profileService: ProfileService,
    private val memberProfileRepository: MemberProfileRepository,
    private val termsAgreementRepository: TermsAgreementRepository,
    transactionManager: PlatformTransactionManager,
) : ContextTest() {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    private fun signUp(providerId: String): UUID {
        return socialAuthService.authenticate(SocialLoginProvider.GOOGLE, providerId, Email("user@example.com"))
    }

    @Test
    fun `가입하면 필수 약관 동의와 빈 프로필이 함께 만들어진다`() {
        // when — 시드 필수 약관 2건: SERVICE·PRIVACY v1.0
        val memberId = signUp("google-sub-p1")

        // then — 프로필은 회원당 항상 하나 존재한다
        assertThat(termsAgreementRepository.findByMemberIdAndDeletedAtIsNull(memberId)).hasSize(2)
        val profile = profileService.getProfile(memberId)
        assertThat(profile.bio).isEmpty()
        assertThat(profile.interestJobRoleIds).isEmpty()
        assertThat(profile.interestCompanyIds).isEmpty()
    }

    @Test
    fun `프로필 수정은 닉네임과 소개 및 관심 항목을 함께 전체 교체한다`() {
        // given — 참조 id 는 seed.sql: jobRole 1(서버·백엔드)·2(프론트엔드), company 1·2·3
        val memberId = signUp("google-sub-p5")

        // when
        val updatedId = profileService.update(
            memberId,
            Nickname("변경된 닉네임 01"),
            ProfileContent(
                bio = "자기소개",
                interestJobRoleIds = listOf(1L, 2L),
                interestCompanyIds = listOf(1L, 2L),
            ),
        )
        profileService.update(
            memberId,
            Nickname("변경된 닉네임 01"),
            ProfileContent(
                bio = "자기소개",
                interestJobRoleIds = listOf(2L),
                interestCompanyIds = listOf(3L),
            ),
        )

        // then — 쓰기는 식별자만 반환하고, 다건은 통째로 교체된다
        assertThat(updatedId).isEqualTo(memberId)
        assertThat(memberService.getMember(memberId).nickname).isEqualTo(Nickname("변경된 닉네임 01"))
        val found = profileService.getProfile(memberId)
        assertThat(found.bio).isEqualTo("자기소개")
        assertThat(found.interestJobRoleIds).containsExactly(2L)
        assertThat(found.interestCompanyIds).containsExactly(3L)
    }

    @Test
    fun `다른 회원의 닉네임으로 프로필을 수정하면 E1007 을 던지고 프로필을 변경하지 않는다`() {
        val first = signUp("google-sub-p7")
        val second = signUp("google-sub-p8")
        val firstNickname = memberService.getMember(first).nickname

        assertThatThrownBy {
            profileService.update(
                second,
                firstNickname,
                ProfileContent(
                    bio = "저장되지 않을 소개",
                    interestJobRoleIds = emptyList(),
                    interestCompanyIds = emptyList(),
                ),
            )
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.NICKNAME_DUPLICATED)
        }

        assertThat(profileService.getProfile(second).bio).isEmpty()
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `프로필 저장이 실패하면 앞서 변경한 닉네임도 롤백한다`() {
        val memberId = signUp("google-sub-p6")
        val originalNickname = memberService.getMember(memberId).nickname
        transactionTemplate.executeWithoutResult {
            memberProfileRepository.findByMemberIdAndDeletedAtIsNull(memberId)!!
                .delete(LocalDateTime.of(2026, 8, 18, 0, 0))
        }

        assertThatThrownBy {
            profileService.update(
                memberId,
                Nickname("롤백 닉네임 01"),
                ProfileContent(
                    bio = "저장되지 않을 소개",
                    interestJobRoleIds = emptyList(),
                    interestCompanyIds = emptyList(),
                ),
            )
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.PROFILE_NOT_FOUND)
        }

        assertThat(memberService.getMember(memberId).nickname).isEqualTo(originalNickname)
    }
}
