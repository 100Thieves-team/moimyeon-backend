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
        val entity = requireFound(memberProfileRepository.findByMemberIdAndDeletedAtIsNull(memberId), CoreErrorType.PROFILE_NOT_FOUND)
        return ProfileMapper.toDomain(
            entity,
            interestJobRoleIds = interestJobRoleRepository.findByProfileIdAndDeletedAtIsNull(entity.id).map { it.jobRoleId },
            interestCompanyIds = interestCompanyRepository.findByProfileIdAndDeletedAtIsNull(entity.id).map { it.companyId },
        )
    }

    fun exists(memberId: UUID): Boolean = memberProfileRepository.existsByMemberIdAndDeletedAtIsNull(memberId)
}
