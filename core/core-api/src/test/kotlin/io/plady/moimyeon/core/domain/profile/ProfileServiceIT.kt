package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.domain.member.Email
import io.plady.moimyeon.core.domain.member.SocialAuthService
import io.plady.moimyeon.core.enums.MeetingPreference
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.storage.db.core.TermsAgreementRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// 이슈 Verification: 약관 동의 → 닉네임 자동 생성 → 프로필 저장 → 완성 상태 전환 흐름 재현
@Transactional
class ProfileServiceIT(
    private val socialAuthService: SocialAuthService,
    private val profileService: ProfileService,
    private val termsAgreementRepository: TermsAgreementRepository,
) : ContextTest() {
    private fun signUp(providerId: String): UUID {
        return socialAuthService.authenticate(SocialLoginProvider.GOOGLE, providerId, Email("user@example.com"))
    }

    @Test
    fun `가입(자동 동의)부터 프로필 저장·완성 상태 전환까지의 흐름이 동작한다`() {
        // given — 가입하면 필수 약관 동의가 기록된다 (시드 2건: SERVICE·PRIVACY v1.0)
        val memberId = signUp("google-sub-p1")
        assertThat(termsAgreementRepository.findByMemberId(memberId)).hasSize(2)
        assertThat(profileService.hasProfile(memberId)).isFalse()

        // when — 자동 생성 닉네임으로 프로필 저장
        val nickname = profileService.suggestNickname()
        val created = profileService.create(
            // jobRoleId=1(서버·백엔드), sigunguId=1(강남구) — seed.sql 참조 데이터
            MemberProfile(memberId, nickname, jobRoleId = 1L, bio = null, meetingPreference = null, sigunguId = 1L),
        )

        // then — 완성 상태(프로필 존재)로 전환
        assertThat(created.nickname).isEqualTo(nickname)
        assertThat(profileService.getProfile(memberId).nickname).isEqualTo(nickname)
        assertThat(profileService.isNicknameAvailable(nickname.value)).isFalse()
    }

    @Test
    fun `프로필 수정은 닉네임 유지·변경과 관심 회사·면접 단계 교체를 지원한다`() {
        // given
        val memberId = signUp("google-sub-p5")
        profileService.create(MemberProfile(memberId, Nickname("수정 전 닉네임 01"), null, null, null, null))

        // when — 같은 닉네임을 유지한 채 나머지 필드 교체 (자기 닉네임은 중복이 아니다)
        // 참조 id 는 seed.sql: jobRoleId 1(서버·백엔드)·2(프론트엔드), sigunguId 2(마포구), company 1·2·3
        profileService.update(
            MemberProfile(
                memberId,
                Nickname("수정 전 닉네임 01"),
                jobRoleId = 1L,
                bio = "자기소개",
                meetingPreference = MeetingPreference.BOTH,
                sigunguId = 2L,
                interestCompanyIds = listOf(1L, 2L),
            ),
        )
        // 닉네임 변경도 가능
        profileService.update(
            MemberProfile(
                memberId,
                Nickname("수정 후 닉네임 02"),
                jobRoleId = 2L,
                bio = "자기소개",
                meetingPreference = MeetingPreference.BOTH,
                sigunguId = 2L,
                interestCompanyIds = listOf(3L),
            ),
        )

        // then
        val found = profileService.getProfile(memberId)
        assertThat(found.nickname).isEqualTo(Nickname("수정 후 닉네임 02"))
        assertThat(found.jobRoleId).isEqualTo(2L)
        assertThat(found.interestCompanyIds).containsExactly(3L)
        assertThat(profileService.isNicknameAvailable("수정 전 닉네임 01")).isTrue()
    }

    @Test
    fun `이미 사용 중인 닉네임으로는 저장이 거부된다`() {
        // given
        val first = signUp("google-sub-p2")
        val second = signUp("google-sub-p3")
        val nickname = Nickname("차분한 펭귄 12")
        profileService.create(MemberProfile(first, nickname, null, null, null, null))

        // when & then
        assertThatThrownBy {
            profileService.create(MemberProfile(second, nickname, null, null, null, null))
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.NICKNAME_DUPLICATED)
        }
    }

    @Test
    fun `같은 회원은 프로필을 다시 작성할 수 없다`() {
        // given
        val memberId = signUp("google-sub-p4")
        profileService.create(MemberProfile(memberId, Nickname("명랑한 해달 33"), null, null, null, null))

        // when & then
        assertThatThrownBy {
            profileService.create(MemberProfile(memberId, Nickname("명랑한 해달 34"), null, null, null, null))
        }.isInstanceOfSatisfying(CoreException::class.java) {
            assertThat(it.errorType).isEqualTo(CoreErrorType.PROFILE_ALREADY_EXISTS)
        }
    }
}
