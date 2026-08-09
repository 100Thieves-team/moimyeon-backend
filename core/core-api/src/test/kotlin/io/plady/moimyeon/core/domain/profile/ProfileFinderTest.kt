package io.plady.moimyeon.core.domain.profile

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.plady.moimyeon.storage.db.core.MemberProfileEntity
import io.plady.moimyeon.storage.db.core.MemberProfileInterestCompanyEntity
import io.plady.moimyeon.storage.db.core.MemberProfileInterestCompanyRepository
import io.plady.moimyeon.storage.db.core.MemberProfileInterestJobRoleEntity
import io.plady.moimyeon.storage.db.core.MemberProfileInterestJobRoleRepository
import io.plady.moimyeon.storage.db.core.MemberProfileRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ProfileFinderTest {
    private val memberProfileRepository = mockk<MemberProfileRepository>()
    private val interestCompanyRepository = mockk<MemberProfileInterestCompanyRepository>()
    private val interestJobRoleRepository = mockk<MemberProfileInterestJobRoleRepository>()
    private val finder = ProfileFinder(
        memberProfileRepository,
        interestCompanyRepository,
        interestJobRoleRepository,
    )

    @Test
    fun `여러 회원의 프로필과 관심 정보를 배치 조회한다`() {
        val firstMemberId = UUID.randomUUID()
        val secondMemberId = UUID.randomUUID()
        val firstProfile = MemberProfileEntity(UUID.randomUUID(), firstMemberId)
        val secondProfile = MemberProfileEntity(UUID.randomUUID(), secondMemberId)
        val memberIds = listOf(firstMemberId, secondMemberId)
        val profileIds = listOf(firstProfile.id, secondProfile.id)
        every { memberProfileRepository.findByMemberIdInAndDeletedAtIsNull(memberIds) } returns listOf(firstProfile, secondProfile)
        every { interestJobRoleRepository.findByProfileIdInAndDeletedAtIsNull(profileIds) } returns listOf(
            MemberProfileInterestJobRoleEntity(firstProfile.id, 101L),
            MemberProfileInterestJobRoleEntity(secondProfile.id, 102L),
        )
        every { interestCompanyRepository.findByProfileIdInAndDeletedAtIsNull(profileIds) } returns listOf(
            MemberProfileInterestCompanyEntity(firstProfile.id, 201L),
        )

        val profiles = finder.getAllByMemberIds(memberIds)

        assertThat(profiles).extracting("memberId").containsExactly(firstMemberId, secondMemberId)
        assertThat(profiles[0].interestJobRoleIds).containsExactly(101L)
        assertThat(profiles[0].interestCompanyIds).containsExactly(201L)
        assertThat(profiles[1].interestJobRoleIds).containsExactly(102L)
        assertThat(profiles[1].interestCompanyIds).isEmpty()
        verify(exactly = 1) { memberProfileRepository.findByMemberIdInAndDeletedAtIsNull(memberIds) }
        verify(exactly = 1) { interestJobRoleRepository.findByProfileIdInAndDeletedAtIsNull(profileIds) }
        verify(exactly = 1) { interestCompanyRepository.findByProfileIdInAndDeletedAtIsNull(profileIds) }
    }
}
