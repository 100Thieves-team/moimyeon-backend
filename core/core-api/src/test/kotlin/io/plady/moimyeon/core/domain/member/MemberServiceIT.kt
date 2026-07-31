package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
    fun `자동 부여된 닉네임은 사용 불가로 판정되고, 닉네임 변경은 자기 닉네임 유지를 허용한다`() {
        // given
        val memberId = signUp("google-sub-n1")
        val assigned = memberRepository.findById(memberId).get().nickname

        // when & then — 부여된 닉네임은 전체 기준 사용 불가
        assertThat(memberService.isNicknameAvailable(assigned)).isFalse()

        // 자기 닉네임 그대로 변경 요청은 허용된다
        memberService.changeNickname(memberId, Nickname(assigned))
        assertThat(memberRepository.findById(memberId).get().nickname).isEqualTo(assigned)
    }

    @Test
    fun `닉네임을 변경하면 저장되고, 다른 회원이 쓰는 닉네임으로는 변경할 수 없다`() {
        // given
        val first = signUp("google-sub-n2")
        val second = signUp("google-sub-n3")
        memberService.changeNickname(first, Nickname("변경된 닉네임 01"))

        // when & then
        assertThat(memberRepository.findById(first).get().nickname).isEqualTo("변경된 닉네임 01")
        assertThatThrownBy { memberService.changeNickname(second, Nickname("변경된 닉네임 01")) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.NICKNAME_DUPLICATED)
            }
    }
}
