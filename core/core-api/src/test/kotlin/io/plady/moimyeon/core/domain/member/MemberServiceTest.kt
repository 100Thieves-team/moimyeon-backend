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
import org.springframework.dao.DataIntegrityViolationException
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

    @Test
    fun `동시 변경으로 유니크 충돌이 나면 E1007 로 매핑하고, 그 외 무결성 위반은 전파한다`() {
        every { memberFinder.getById(memberId) } returns member
        // Nickname 은 value class 라 mockk any() 매처가 깨져 구체 인자로 스텁한다
        every { memberFinder.isNicknameAvailableFor(memberId, Nickname("명랑한 해달 33")) } returns true
        every { memberFinder.isNicknameAvailableFor(memberId, Nickname("성실한 치타 77")) } returns true
        every { memberManager.changeNickname(memberId, Nickname("명랑한 해달 33")) } throws
            DataIntegrityViolationException("uk_member_nickname")
        every { memberManager.changeNickname(memberId, Nickname("성실한 치타 77")) } throws
            DataIntegrityViolationException("NULL not allowed for column")

        assertThatThrownBy { memberService.changeNickname(memberId, "명랑한 해달 33") }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.NICKNAME_DUPLICATED)
            }
        assertThatThrownBy { memberService.changeNickname(memberId, "성실한 치타 77") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
