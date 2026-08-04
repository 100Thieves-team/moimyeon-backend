package io.plady.moimyeon.core.domain.member

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.MemberEntity
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class MemberValidatorTest {
    private val memberRepository = mockk<MemberRepository>()
    private val validator = MemberValidator(memberRepository)

    private val memberId = UUID.randomUUID()

    @Test
    fun `활성 회원이면 이용 가능한 회원으로 판정한다`() {
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns member(MemberStatus.ACTIVE)

        assertThatCode { validator.validateActive(memberId) }.doesNotThrowAnyException()
    }

    @Test
    fun `이용 제한 회원이면 MEMBER_NOT_ACTIVE 로 거부한다`() {
        every {
            memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId)
        } returns member(MemberStatus.RESTRICTED)

        assertThatThrownBy { validator.validateActive(memberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_ACTIVE)
            }
    }

    @Test
    fun `존재하지 않는 회원이면 MEMBER_NOT_FOUND 로 거부한다`() {
        every { memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId) } returns null

        assertThatThrownBy { validator.validateActive(memberId) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.MEMBER_NOT_FOUND)
            }
    }

    private fun member(memberStatus: MemberStatus): MemberEntity = mockk {
        every { status } returns memberStatus
    }
}
