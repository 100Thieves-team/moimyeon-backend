package io.plady.moimyeon.storage.db.core

import io.plady.moimyeon.core.enums.MeetingPreference
import io.plady.moimyeon.core.enums.MemberStatus
import io.plady.moimyeon.core.enums.SocialLoginProvider
import io.plady.moimyeon.storage.db.CoreDbContextTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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

    // member_profile.member_id 가 실제 회원을 가리키도록 회원을 먼저 저장한다(FK 는 없지만 데이터 정합 유지).
    private fun persistMember(providerId: String): UUID {
        val member = MemberEntity(
            id = UUID.randomUUID(),
            email = "user@example.com",
            nickname = "nick-$providerId",
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
        assertThat(found!!.bio).isEqualTo("자기소개")
        assertThat(found.meetingPreference).isEqualTo(MeetingPreference.BOTH)
    }

    @Test
    fun `관심 회사 컬렉션이 함께 저장되고 조회된다`() {
        // given
        val memberId = persistMember("google-sub-5")
        val companyIds = mutableListOf(persistCompany("달빛페이"), persistCompany("한빛커머스"))
        memberProfileRepository.saveAndFlush(
            MemberProfileEntity(memberId = memberId, interestCompanyIds = companyIds),
        )

        // when
        val found = memberProfileRepository.findById(memberId).orElse(null)

        // then
        assertThat(found!!.interestCompanyIds).containsExactlyInAnyOrderElementsOf(companyIds)
    }
}
