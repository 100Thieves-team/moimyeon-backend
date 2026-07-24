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
    val companyRepository: CompanyRepository,
    val jobGroupRepository: JobGroupRepository,
    val jobRoleRepository: JobRoleRepository,
) : CoreDbContextTest() {
    private fun persistCompany(name: String): Long {
        return companyRepository.saveAndFlush(CompanyEntity(corpCode = null, nameKr = name, nameNormalized = name)).id
    }

    private fun persistJobRole(): Long {
        val group = jobGroupRepository.saveAndFlush(JobGroupEntity(code = "TEST_직군", displayName = "테스트 직군", sortOrder = 1))
        return jobRoleRepository.saveAndFlush(
            JobRoleEntity(jobGroupId = group.id, code = "TEST_직무", displayName = "테스트 직무", sortOrder = 1),
        ).id
    }

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
                jobRoleId = persistJobRole(),
                bio = "자기소개",
                meetingPreference = MeetingPreference.BOTH,
                sigunguId = null,
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
    fun `관심 회사 컬렉션이 함께 저장되고 조회된다`() {
        // given
        val memberId = persistMember("google-sub-5")
        val companyIds = mutableListOf(persistCompany("달빛페이"), persistCompany("한빛커머스"))
        memberProfileRepository.saveAndFlush(
            MemberProfileEntity(memberId = memberId, nickname = "관심회사 보유 01", interestCompanyIds = companyIds),
        )

        // when
        val found = memberProfileRepository.findById(memberId).orElse(null)

        // then
        assertThat(found!!.interestCompanyIds).containsExactlyInAnyOrderElementsOf(companyIds)
    }

    @Test
    fun `자신을 제외한 닉네임 존재 여부를 확인한다`() {
        // given
        val mine = persistMember("google-sub-6")
        memberProfileRepository.saveAndFlush(MemberProfileEntity(memberId = mine, nickname = "내 닉네임 01"))

        // when & then — 자기 닉네임은 중복이 아니고, 남이 보면 중복이다
        assertThat(memberProfileRepository.existsByNicknameAndMemberIdNot("내 닉네임 01", mine)).isFalse()
        assertThat(memberProfileRepository.existsByNicknameAndMemberIdNot("내 닉네임 01", UUID.randomUUID())).isTrue()
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
