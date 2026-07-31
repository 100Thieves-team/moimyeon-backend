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
    val interestCompanyRepository: MemberProfileInterestCompanyRepository,
    val interestJobRoleRepository: MemberProfileInterestJobRoleRepository,
) : CoreDbContextTest() {
    private fun persistCompany(name: String): Long {
        return companyRepository.saveAndFlush(CompanyEntity(corpCode = null, nameKr = name, nameNormalized = name)).id
    }

    // code 는 전역 유니크라 호출마다 달라야 한다. 직군은 한 번만 만들어 재사용한다.
    private fun persistJobRole(code: String = "TEST_직무"): Long {
        val group = jobGroupRepository.findAll().firstOrNull()
            ?: jobGroupRepository.saveAndFlush(JobGroupEntity(code = "TEST_직군", displayName = "테스트 직군", sortOrder = 1))
        return jobRoleRepository.saveAndFlush(
            JobRoleEntity(jobGroupId = group.id, code = code, displayName = "테스트 직무", sortOrder = 1),
        ).id
    }

    private val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

    private fun persistProfile(memberId: UUID, bio: String? = null): UUID {
        return memberProfileRepository.saveAndFlush(
            MemberProfileEntity(id = UUID.randomUUID(), memberId = memberId, bio = bio ?: ""),
        ).id
    }

    // member_profile.member_id 가 실제 회원을 가리키도록 회원을 먼저 저장한다(FK 는 없지만 데이터 정합 유지).
    private fun persistMember(providerId: String): UUID {
        val member = MemberEntity(
            id = UUID.randomUUID(),
            email = "user@example.com",
            nickname = "nick-$providerId",
            status = MemberStatus.ACTIVE,
            lastLoginAt = now,
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
                id = UUID.randomUUID(),
                memberId = memberId,
                bio = "자기소개",
                meetingPreference = MeetingPreference.BOTH,
                sigunguId = null,
            ),
        )

        // when
        val found = memberProfileRepository.findByMemberIdAndDeletedAtIsNull(memberId)

        // then
        assertThat(found).isNotNull
        assertThat(found!!.bio).isEqualTo("자기소개")
        assertThat(found.meetingPreference).isEqualTo(MeetingPreference.BOTH)
    }

    @Test
    fun `관심 회사 조인이 프로필별로 조회된다`() {
        // given
        val profileId = persistProfile(persistMember("google-sub-5"))
        val companyIds = listOf(persistCompany("달빛페이"), persistCompany("한빛커머스"))
        interestCompanyRepository.saveAllAndFlush(
            companyIds.map { MemberProfileInterestCompanyEntity(profileId = profileId, companyId = it) },
        )

        // when
        val found = interestCompanyRepository.findByProfileIdAndDeletedAtIsNull(profileId)

        // then
        assertThat(found.map { it.companyId }).containsExactlyInAnyOrderElementsOf(companyIds)
    }

    @Test
    fun `관심 직무는 소프트 삭제되고 같은 직무를 다시 담으면 새 행이 된다`() {
        // given — 한 사람이 여러 직무를 함께 준비하는 경우
        val profileId = persistProfile(persistMember("google-sub-7"))
        val backend = persistJobRole("TEST_서버_백엔드")
        val frontend = persistJobRole("TEST_프론트엔드")
        val saved = interestJobRoleRepository.saveAllAndFlush(
            listOf(backend, frontend).map { MemberProfileInterestJobRoleEntity(profileId = profileId, jobRoleId = it) },
        )
        assertThat(interestJobRoleRepository.findByProfileIdAndDeletedAtIsNull(profileId).map { it.jobRoleId })
            .containsExactlyInAnyOrder(backend, frontend)

        // when — 백엔드를 관심에서 뺀다(소프트 삭제)
        saved.first { it.jobRoleId == backend }.delete(now)
        interestJobRoleRepository.flush()

        // then — 조회에서는 빠지지만 행은 남는다
        assertThat(interestJobRoleRepository.findByProfileIdAndDeletedAtIsNull(profileId).map { it.jobRoleId })
            .containsExactly(frontend)
        assertThat(interestJobRoleRepository.count()).isEqualTo(2)

        // when — 뺐던 직무를 다시 담는다. _active_check 덕분에 유니크와 충돌하지 않는다
        interestJobRoleRepository.saveAndFlush(MemberProfileInterestJobRoleEntity(profileId = profileId, jobRoleId = backend))

        // then — 되살리기가 아니라 새 행이라 "언제부터 관심인지"가 새로 기록된다
        assertThat(interestJobRoleRepository.findByProfileIdAndDeletedAtIsNull(profileId).map { it.jobRoleId })
            .containsExactlyInAnyOrder(backend, frontend)
        assertThat(interestJobRoleRepository.count()).isEqualTo(3)
    }

    @Test
    fun `소프트 삭제된 프로필은 조회에서 빠지지만 행은 남는다`() {
        // given
        val memberId = persistMember("google-sub-6")
        val profile = memberProfileRepository.saveAndFlush(
            MemberProfileEntity(id = UUID.randomUUID(), memberId = memberId, bio = "자기소개"),
        )

        // when
        profile.delete(now)
        memberProfileRepository.flush()

        // then
        assertThat(memberProfileRepository.findByMemberIdAndDeletedAtIsNull(memberId)).isNull()
        assertThat(memberProfileRepository.existsByMemberIdAndDeletedAtIsNull(memberId)).isFalse()
        assertThat(memberProfileRepository.findById(profile.id)).isPresent()
    }
}
