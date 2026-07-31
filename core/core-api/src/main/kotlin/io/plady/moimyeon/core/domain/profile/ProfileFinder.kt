package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberProfileRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class ProfileFinder(
    private val memberProfileRepository: MemberProfileRepository,
    private val profileInterestManager: ProfileInterestManager,
) {
    @Transactional(readOnly = true)
    fun getProfile(memberId: UUID): MemberProfile {
        val entity = requireFound(memberProfileRepository.findByMemberIdAndDeletedAtIsNull(memberId), CoreErrorType.PROFILE_NOT_FOUND)
        return ProfileMapper.toDomain(
            entity,
            interestJobRoleIds = profileInterestManager.findJobRoleIds(memberId),
            interestCompanyIds = profileInterestManager.findCompanyIds(memberId),
        )
    }

    @Transactional(readOnly = true)
    fun exists(memberId: UUID): Boolean = memberProfileRepository.existsByMemberIdAndDeletedAtIsNull(memberId)
}
