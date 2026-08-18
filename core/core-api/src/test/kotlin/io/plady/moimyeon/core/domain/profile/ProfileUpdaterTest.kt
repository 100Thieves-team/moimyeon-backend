package io.plady.moimyeon.core.domain.profile

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.member.MemberManager
import io.plady.moimyeon.core.domain.member.Nickname
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ProfileUpdaterTest {
    private val memberManager = mockk<MemberManager>()
    private val profileManager = mockk<ProfileManager>()
    private val profileUpdater = ProfileUpdater(memberManager, profileManager)

    @Test
    fun `닉네임을 변경한 뒤 프로필 전체를 교체한다`() {
        val memberId = UUID.randomUUID()
        val nickname = Nickname("명랑한 해달 33")
        val content = ProfileContent(
            bio = "자기소개",
            interestJobRoleIds = listOf(1L),
            interestCompanyIds = listOf(2L),
        )
        every { memberManager.changeNickname(memberId, nickname) } just Runs
        every { profileManager.update(memberId, content) } returns memberId

        assertThat(profileUpdater.update(memberId, nickname, content)).isEqualTo(memberId)

        verifyOrder {
            memberManager.changeNickname(memberId, nickname)
            profileManager.update(memberId, content)
        }
    }
}
