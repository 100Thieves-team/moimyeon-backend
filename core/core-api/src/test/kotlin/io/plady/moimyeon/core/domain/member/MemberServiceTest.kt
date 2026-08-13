package io.plady.moimyeon.core.domain.member

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class MemberServiceTest {
    private val memberFinder = mockk<MemberFinder>()
    private val memberManager = mockk<MemberManager>()
    private val nicknameGenerator = mockk<NicknameGenerator>()
    private val memberService = MemberService(memberFinder, memberManager, nicknameGenerator)

    private val memberId = UUID.randomUUID()

    @Test
    fun `여러 회원 조회는 Finder 결과를 그대로 반환한다`() {
        val memberIds = listOf(UUID.randomUUID(), UUID.randomUUID())
        val members = listOf(mockk<Member>(), mockk<Member>())
        every { memberFinder.getAllByIds(memberIds) } returns members

        val result = memberService.getMembers(memberIds)

        assertThat(result).containsExactlyElementsOf(members)
    }

    @Test
    fun `닉네임 형식이 틀리면 사용 가능 여부 확인은 E1005 를 던진다`() {
        assertThatThrownBy { memberService.isNicknameAvailable("금지문자!@#") }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.INVALID_NICKNAME)
            }
    }

    @Test
    fun `닉네임 변경은 Manager 에 위임한다`() {
        every { memberManager.changeNickname(memberId, Nickname("명랑한 해달 33")) } just Runs

        memberService.changeNickname(memberId, Nickname("명랑한 해달 33"))

        verify { memberManager.changeNickname(memberId, Nickname("명랑한 해달 33")) }
    }

    @Test
    fun `Manager 가 던진 도메인 에러는 그대로 전파한다`() {
        every {
            memberManager.changeNickname(memberId, Nickname("명랑한 해달 33"))
        } throws CoreException(CoreErrorType.NICKNAME_DUPLICATED)

        assertThatThrownBy { memberService.changeNickname(memberId, Nickname("명랑한 해달 33")) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.NICKNAME_DUPLICATED)
            }
    }
}
