package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.MeetingPreference
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.CoreDbContextTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Transactional
class MemberProfileRepositoryIT(
    val memberProfileRepository: MemberProfileRepository,
    val memberRepository: MemberRepository,
) : CoreDbContextTest() {
    private val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

    // member_profile.member_id 는 member(id) 를 참조(FK)하므로 회원을 먼저 저장한다.
    private fun persistMember(providerId: String): UUID {
        val member = MemberEntity(
            id = UUID.randomUUID(),
            email = "user@example.com",
            status = MemberStatus.ACTIVE,
            lastLoginAt = now,
            withdrawnAt = null,
            socialAccounts = mutableListOf(
                SocialAccountEntity(SocialLoginProvider.GOOGLE, providerId, "user@example.com"),
            ),
        )
        return memberRepository.saveAndFlush(member).id
    }

    @Test
    fun `프로필을 저장하고 member_id 로 조회한다`() {
        // given
        val memberId = persistMember("google-sub-1")
        memberProfileRepository.saveAndFlush(
            MemberProfileEntity(
                memberId = memberId,
                nickname = "차분한 펭귄 12",
                jobTitle = "백엔드 개발",
                bio = "자기소개",
                meetingPreference = MeetingPreference.BOTH,
                region = "서울 · 마포구",
            ),
        )

        // when
        val found = memberProfileRepository.findById(memberId).orElse(null)

        // then
        assertThat(found).isNotNull
        assertThat(found!!.nickname).isEqualTo("차분한 펭귄 12")
        assertThat(found.meetingPreference).isEqualTo(MeetingPreference.BOTH)
    }

    @Test
    fun `닉네임 존재 여부를 확인한다`() {
        // given
        val memberId = persistMember("google-sub-2")
        memberProfileRepository.saveAndFlush(MemberProfileEntity(memberId = memberId, nickname = "명랑한 해달 33"))

        // when & then
        assertThat(memberProfileRepository.existsByNickname("명랑한 해달 33")).isTrue()
        assertThat(memberProfileRepository.existsByNickname("없는 닉네임")).isFalse()
    }

    @Test
    fun `같은 닉네임은 유니크 제약으로 중복 저장되지 않는다`() {
        // given
        val first = persistMember("google-sub-3")
        val second = persistMember("google-sub-4")
        memberProfileRepository.saveAndFlush(MemberProfileEntity(memberId = first, nickname = "중복 닉네임 01"))

        // when & then
        assertThatThrownBy {
            memberProfileRepository.saveAndFlush(MemberProfileEntity(memberId = second, nickname = "중복 닉네임 01"))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
