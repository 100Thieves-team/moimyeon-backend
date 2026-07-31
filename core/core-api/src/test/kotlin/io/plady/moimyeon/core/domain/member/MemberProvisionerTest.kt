package io.plady.moimyeon.core.domain.member

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.domain.profile.ProfileManager
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
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID

class MemberProvisionerTest {
    private val memberManager = mockk<MemberManager>()
    private val nicknameGenerator = mockk<NicknameGenerator>()
    private val termsFinder = mockk<TermsFinder>()
    private val termsAgreementRecorder = mockk<TermsAgreementRecorder>()

    private val profileManager = mockk<ProfileManager>(relaxed = true)

    // 트랜잭션 경계는 relaxed 매니저를 물린 실제 TransactionTemplate 로 통과시킨다(커밋/롤백은 no-op)
    private val memberProvisioner = MemberProvisioner(
        memberManager,
        nicknameGenerator,
        termsFinder,
        termsAgreementRecorder,
        profileManager,
        TransactionTemplate(mockk(relaxed = true)),
    )

    private val provider = SocialLoginProvider.GOOGLE
    private val email = Email("user@example.com")
    private val nickname = Nickname("차분한 펭귄 12")

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
    fun `회원을 생성하고 현재 유효한 필수 약관 동의를 모두 기록한다`() {
        // given
        val newMemberId = UUID.randomUUID()
        val termsId = UUID.randomUUID()
        every { nicknameGenerator.generateUnique() } returns nickname
        every { memberManager.append(provider, "sub-1", email, nickname) } returns newMemberId
        every { termsFinder.findRequiredActive() } returns listOf(requiredTerms(termsId))
        every { termsAgreementRecorder.recordAll(newMemberId, listOf(termsId), any()) } just Runs

        // when
        val result = memberProvisioner.provision(provider, "sub-1", email)

        // then
        assertThat(result).isEqualTo(newMemberId)
        verify(exactly = 1) { memberManager.append(provider, "sub-1", email, nickname) }
        verify(exactly = 1) { termsAgreementRecorder.recordAll(newMemberId, listOf(termsId), any()) }
    }

    @Test
    fun `닉네임 동시 충돌이 나면 새 닉네임으로 1회 재시도한다`() {
        // given — 첫 시도는 uk_member_nickname 충돌, 두 번째 후보로 성공
        val newMemberId = UUID.randomUUID()
        val retryNickname = Nickname("명랑한 해달 33")
        every { nicknameGenerator.generateUnique() } returns nickname andThen retryNickname
        every { memberManager.append(provider, "sub-2", email, nickname) } throws
            DataIntegrityViolationException("uk_member_nickname")
        every { memberManager.append(provider, "sub-2", email, retryNickname) } returns newMemberId
        every { termsFinder.findRequiredActive() } returns emptyList()
        every { termsAgreementRecorder.recordAll(newMemberId, emptyList(), any()) } just Runs

        // when
        val result = memberProvisioner.provision(provider, "sub-2", email)

        // then
        assertThat(result).isEqualTo(newMemberId)
    }

    @Test
    fun `재시도까지 닉네임이 충돌하면 E1007 로 닫는다`() {
        // given — 두 시도 모두 충돌. 마지막 시도가 500 으로 새지 않아야 한다.
        // Nickname 은 value class 라 mockk any() 매처가 깨져 구체 인자로 스텁한다.
        val retryNickname = Nickname("명랑한 해달 33")
        every { nicknameGenerator.generateUnique() } returns nickname andThen retryNickname
        every { memberManager.append(provider, "sub-3", email, nickname) } throws
            DataIntegrityViolationException("uk_member_nickname")
        every { memberManager.append(provider, "sub-3", email, retryNickname) } throws
            DataIntegrityViolationException("uk_member_nickname")

        // when & then
        assertThatThrownBy { memberProvisioner.provision(provider, "sub-3", email) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.NICKNAME_DUPLICATED)
            }
    }

    @Test
    fun `동시 가입으로 소셜 계정 유니크 충돌이 나면 E1004 로 매핑한다`() {
        // given
        every { nicknameGenerator.generateUnique() } returns nickname
        every { memberManager.append(provider, "sub-4", email, nickname) } throws
            DataIntegrityViolationException("uk_social_account_provider_provider_id")

        // when & then
        assertThatThrownBy { memberProvisioner.provision(provider, "sub-4", email) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.SOCIAL_ACCOUNT_ALREADY_LINKED)
            }
    }

    @Test
    fun `기대하지 않은 무결성 위반은 오인 매핑하지 않고 전파한다`() {
        // given — not-null 위반 등은 어떤 도메인 에러도 아니다
        every { nicknameGenerator.generateUnique() } returns nickname
        every { memberManager.append(provider, "sub-5", email, nickname) } throws
            DataIntegrityViolationException("NULL not allowed for column EMAIL")

        // when & then
        assertThatThrownBy { memberProvisioner.provision(provider, "sub-5", email) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
            .isNotInstanceOf(CoreException::class.java)
    }
}
