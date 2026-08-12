package io.plady.moimyeon.core.api.facade

import io.mockk.every
import io.mockk.mockk
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.Member
import io.plady.moimyeon.core.domain.member.MemberService
import io.plady.moimyeon.core.domain.member.Nickname
import io.plady.moimyeon.core.domain.profile.MemberProfile
import io.plady.moimyeon.core.domain.profile.ProfileService
import io.plady.moimyeon.core.domain.trust.PublicTrust
import io.plady.moimyeon.core.domain.trust.TrustService
import io.plady.moimyeon.core.enums.MeetingPreference
import io.plady.moimyeon.core.enums.SocialLoginProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PublicProfileFacadeTest {
    private val memberService = mockk<MemberService>()
    private val profileService = mockk<ProfileService>()
    private val trustService = mockk<TrustService>()
    private val facade = PublicProfileFacade(memberService, profileService, trustService)

    @Test
    fun `회원 프로필 신뢰 정보를 공개 응답으로 조립한다`() {
        val member = Member.register(
            provider = SocialLoginProvider.GOOGLE,
            providerId = "google-sub-public-profile",
            email = Email("user@example.com"),
            nickname = Nickname("차분한 펭귄 12"),
            now = LocalDateTime.of(2026, 8, 12, 12, 0),
        )
        val profile = MemberProfile(
            memberId = member.id,
            bio = "자기소개",
            meetingPreference = MeetingPreference.BOTH,
            sigunguId = 2L,
            interestJobRoleIds = listOf(1L, 2L),
            interestCompanyIds = listOf(1L, 2L),
        )
        every { memberService.getMember(member.id) } returns member
        every { profileService.getPublicProfile(member.id) } returns profile
        every { trustService.getPublicTrust(member.id) } returns PublicTrust.empty()

        val response = facade.get(member.id)

        assertThat(response.memberId).isEqualTo(member.id)
        assertThat(response.nickname).isEqualTo("차분한 펭귄 12")
        assertThat(response.interestJobRoleIds).containsExactly(1L, 2L)
        assertThat(response.bio).isEqualTo("자기소개")
        assertThat(response.meetingPreference).isEqualTo(MeetingPreference.BOTH)
        assertThat(response.trust.activityTopPercent).isNull()
        assertThat(response.trust.recentAttendances).isEmpty()
        assertThat(response.trust.noShowCount).isZero()
        assertThat(response.trust.representativeTags).isEmpty()
    }
}
