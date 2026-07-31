package io.plady.moimyeon.core.domain.member

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class MemberServiceTest {
    private val memberFinder = mockk<MemberFinder>()
    private val memberManager = mockk<MemberManager>()
    private val nicknameGenerator = mockk<NicknameGenerator>()
    private val memberService = MemberService(memberFinder, memberManager, nicknameGenerator)

    private val member = Member.register(
        SocialLoginProvider.GOOGLE,
        "sub-1",
        Email("user@example.com"),
        Nickname("차분한 펭귄 12"),
        LocalDateTime.of(2026, 1, 1, 0, 0),
    )
    private val memberId = member.id

    @Test
    fun `닉네임 형식이 틀리면 사용 가능 여부 확인은 E1005 를 던진다`() {
        assertThatThrownBy { memberService.isNicknameAvailable("금지문자!@#") }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.INVALID_NICKNAME)
            }
    }

    @Test
    fun `닉네임 변경은 자신을 제외한 유일성을 확인하고 저장한다`() {
        every { memberFinder.getById(memberId) } returns member
        every { memberFinder.isNicknameAvailableFor(memberId, Nickname("명랑한 해달 33")) } returns true
        every { memberManager.changeNickname(memberId, Nickname("명랑한 해달 33")) } just Runs

        memberService.changeNickname(memberId, "명랑한 해달 33")
    }

    @Test
    fun `다른 회원이 쓰는 닉네임으로 변경하면 E1007 을 던진다`() {
        every { memberFinder.getById(memberId) } returns member
        every { memberFinder.isNicknameAvailableFor(memberId, Nickname("명랑한 해달 33")) } returns false

        assertThatThrownBy { memberService.changeNickname(memberId, "명랑한 해달 33") }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.NICKNAME_DUPLICATED)
            }
    }

}
