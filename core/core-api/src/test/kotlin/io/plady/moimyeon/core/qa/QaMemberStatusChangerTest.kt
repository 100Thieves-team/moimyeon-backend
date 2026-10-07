package io.plady.moimyeon.core.qa

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.plady.moimyeon.core.domain.member.Member
import io.plady.moimyeon.core.domain.member.MemberFinder
import io.plady.moimyeon.core.domain.member.MemberManager
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class QaMemberStatusChangerTest {
    private val qaMemberFinder = mockk<QaMemberFinder>()
    private val memberFinder = mockk<MemberFinder>()
    private val memberManager = mockk<MemberManager>()
    private val changer = QaMemberStatusChanger(qaMemberFinder, memberFinder, memberManager)

    private val memberId = UUID.randomUUID()

    @Test
    fun `동시 요청이 먼저 이용 제한으로 바꿨으면 성공으로 본다`() {
        every { qaMemberFinder.requireQaMember(memberId) } just runs
        every { memberFinder.getById(memberId) } returnsMany listOf(member(MemberStatus.ACTIVE), member(MemberStatus.RESTRICTED))
        every { memberManager.restrict(memberId) } throws CoreException(CoreErrorType.MEMBER_NOT_ACTIVE)

        assertThat(changer.change(memberId, MemberStatus.RESTRICTED))
            .isEqualTo(QaMemberStatus(memberId, before = MemberStatus.ACTIVE, status = MemberStatus.RESTRICTED))
    }

    @Test
    fun `바꾸지 못했고 목표 상태도 아니면 예외를 그대로 던진다`() {
        every { qaMemberFinder.requireQaMember(memberId) } just runs
        every { memberFinder.getById(memberId) } returns member(MemberStatus.ACTIVE)
        every { memberManager.restrict(memberId) } throws CoreException(CoreErrorType.MEMBER_NOT_FOUND)

        assertThatThrownBy { changer.change(memberId, MemberStatus.RESTRICTED) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND)
            }
    }

    private fun member(status: MemberStatus): Member = mockk {
        every { this@mockk.status } returns status
    }
}
