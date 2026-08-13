package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberProfileInterestCompanyRepository
import io.plady.moimyeon.storage.db.core.MemberProfileInterestJobRoleRepository
import io.plady.moimyeon.storage.db.core.MemberProfileRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class ProfileFinder(
    private val memberProfileRepository: MemberProfileRepository,
    private val interestCompanyRepository: MemberProfileInterestCompanyRepository,
    private val interestJobRoleRepository: MemberProfileInterestJobRoleRepository,
) {
    @Transactional(readOnly = true)
    fun getProfile(memberId: UUID): MemberProfile {
        return getProfile(memberId, CoreErrorType.PROFILE_NOT_FOUND)
    }

    @Transactional(readOnly = true)
    fun getPublicProfile(memberId: UUID): MemberProfile {
        return getProfile(memberId, CoreErrorType.MEMBER_NOT_FOUND)
    }

    private fun getProfile(memberId: UUID, notFoundError: CoreErrorType): MemberProfile {
        val entity = requireFound(memberProfileRepository.findByMemberIdAndDeletedAtIsNull(memberId), notFoundError)
        return ProfileMapper.toDomain(
            entity,
            interestJobRoleIds = interestJobRoleRepository.findByProfileIdAndDeletedAtIsNull(entity.id).map { it.jobRoleId },
            interestCompanyIds = interestCompanyRepository.findByProfileIdAndDeletedAtIsNull(entity.id).map { it.companyId },
        )
    }

    @Transactional(readOnly = true)
    fun getAllByMemberIds(memberIds: Collection<UUID>): List<MemberProfile> {
        if (memberIds.isEmpty()) return emptyList()
        val profileEntitiesByMemberId = memberProfileRepository
            .findByMemberIdInAndDeletedAtIsNull(memberIds)
            .associateBy { it.memberId }
        val profileIds = profileEntitiesByMemberId.values.map { it.id }
        val jobRoleIdsByProfileId = interestJobRoleRepository
            .findByProfileIdInAndDeletedAtIsNull(profileIds)
            .groupBy({ it.profileId }, { it.jobRoleId })
        val companyIdsByProfileId = interestCompanyRepository
            .findByProfileIdInAndDeletedAtIsNull(profileIds)
            .groupBy({ it.profileId }, { it.companyId })

        return memberIds.distinct().map { memberId ->
            val entity = requireFound(profileEntitiesByMemberId[memberId], CoreErrorType.PROFILE_NOT_FOUND)
            ProfileMapper.toDomain(
                entity,
                interestJobRoleIds = jobRoleIdsByProfileId[entity.id].orEmpty(),
                interestCompanyIds = companyIdsByProfileId[entity.id].orEmpty(),
            )
        }
    }

    fun exists(memberId: UUID): Boolean = memberProfileRepository.existsByMemberIdAndDeletedAtIsNull(memberId)
}
