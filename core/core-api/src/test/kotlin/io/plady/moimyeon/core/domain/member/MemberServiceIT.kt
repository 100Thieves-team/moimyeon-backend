package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Transactional
class MemberServiceIT(
    private val socialAuthService: SocialAuthService,
    private val memberService: MemberService,
    private val memberRepository: MemberRepository,
) : ContextTest() {
    private fun signUp(providerId: String): UUID {
        return socialAuthService.authenticate(SocialLoginProvider.GOOGLE, providerId, Email("user@example.com"))
    }

    @Test
    fun `자동 부여된 닉네임은 사용 불가로 판정된다`() {
        // given
        val memberId = signUp("google-sub-n1")
        val assigned = memberRepository.findById(memberId).get().nickname

        // when & then — 부여된 닉네임은 전체 기준 사용 불가
        assertThat(memberService.isNicknameAvailable(assigned)).isFalse()
    }
}
