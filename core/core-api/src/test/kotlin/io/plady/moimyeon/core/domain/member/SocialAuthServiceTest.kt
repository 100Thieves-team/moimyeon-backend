package io.plady.moimyeon.core.domain.member

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.terms.Terms
import io.plady.moimyeon.core.domain.terms.TermsAgreementRecorder
import io.plady.moimyeon.core.domain.terms.TermsFinder
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.enums.TermsStatus
import io.plady.moimyeon.core.enums.TermsType
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDateTime
import java.util.UUID

class SocialAuthServiceTest {
    private val memberFinder = mockk<MemberFinder>()
    private val memberManager = mockk<MemberManager>()
    private val termsFinder = mockk<TermsFinder>()
    private val termsAgreementRecorder = mockk<TermsAgreementRecorder>()
    private val socialAuthService = SocialAuthService(memberFinder, memberManager, termsFinder, termsAgreementRecorder)

    private val provider = SocialLoginProvider.GOOGLE
    private val email = Email("user@example.com")

    private fun requiredTerms(id: UUID) = Terms(
        id = id,
        type = TermsType.SERVICE,
        version = "v1.0",
        title = "이용약관",
        content = "본문",
        required = true,
        effectiveFrom = LocalDateTime.of(2026, 7, 1, 0, 0),
        status = TermsStatus.ACTIVE,
    )

    @Test
    fun `기존 회원이면 재로그인만 기록하고 같은 id 를 반환하며 새로 가입하지 않는다`() {
        // given
        val existing = Member.register(provider, "sub-1", email, LocalDateTime.of(2026, 1, 1, 0, 0))
        every { memberFinder.existsBySocialAccount(provider, "sub-1") } returns true
        every { memberFinder.getBySocialAccount(provider, "sub-1") } returns existing
        every { memberManager.recordLogin(existing.id) } just Runs

        // when
        val result = socialAuthService.authenticate(provider, "sub-1", email)

        // then
        assertThat(result).isEqualTo(existing.id)
        verify(exactly = 1) { memberManager.recordLogin(existing.id) }
        // append 는 호출되지 않는다(가입 안 함). Email 이 inline value class 라 any() 매처가 깨져 구체 인자로 검증.
        verify(exactly = 0) { memberManager.append(provider, "sub-1", email) }
        verify(exactly = 0) { termsAgreementRecorder.recordAll(any(), any(), any()) }
    }

    @Test
    fun `처음 보는 신원이면 provisioning 하고 현재 유효한 필수 약관 동의를 모두 기록한다`() {
        // given
        val newMemberId = UUID.randomUUID()
        val termsId = UUID.randomUUID()
        every { memberFinder.existsBySocialAccount(provider, "sub-2") } returns false
        every { memberFinder.existsWithdrawnBySocialAccount(provider, "sub-2") } returns false
        every { memberManager.append(provider, "sub-2", email) } returns newMemberId
        every { termsFinder.findRequiredActive() } returns listOf(requiredTerms(termsId))
        every { termsAgreementRecorder.recordAll(newMemberId, listOf(termsId), any()) } just Runs

        // when
        val result = socialAuthService.authenticate(provider, "sub-2", email)

        // then
        assertThat(result).isEqualTo(newMemberId)
        verify(exactly = 1) { memberManager.append(provider, "sub-2", email) }
        verify(exactly = 1) { termsAgreementRecorder.recordAll(newMemberId, listOf(termsId), any()) }
        verify(exactly = 0) { memberManager.recordLogin(any()) }
    }

    @Test
    fun `동시 가입으로 유니크 충돌이 나면 E1004 로 매핑한다`() {
        every { memberFinder.existsBySocialAccount(provider, "sub-4") } returns false
        every { memberFinder.existsWithdrawnBySocialAccount(provider, "sub-4") } returns false
        every { memberManager.append(provider, "sub-4", email) } throws
            DataIntegrityViolationException("uk_social_account_provider_provider_id")

        assertThatThrownBy { socialAuthService.authenticate(provider, "sub-4", email) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.SOCIAL_ACCOUNT_ALREADY_LINKED)
            }
    }

    @Test
    fun `탈퇴 회원이 점유한 신원으로는 재로그인(재가입)할 수 없다`() {
        // given — 탈퇴 회원은 살아있는 회원 조회에서 걸러지고, 점유 가드에서 거부된다
        every { memberFinder.existsBySocialAccount(provider, "sub-3") } returns false
        every { memberFinder.existsWithdrawnBySocialAccount(provider, "sub-3") } returns true

        // when & then
        assertThatThrownBy { socialAuthService.authenticate(provider, "sub-3", email) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_ALREADY_WITHDRAWN)
            }
        verify(exactly = 0) { memberManager.recordLogin(any()) }
        verify(exactly = 0) { memberManager.append(provider, "sub-3", email) }
    }
}
