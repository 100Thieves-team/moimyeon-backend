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
    private val profileInterestFinder: ProfileInterestFinder,
) {
    // 프로필 + 관심직무 + 관심회사 3쿼리를 한 스냅샷으로 읽는다.
    @Transactional(readOnly = true)
    fun getProfile(memberId: UUID): MemberProfile {
        val entity = requireFound(memberProfileRepository.findByMemberIdAndDeletedAtIsNull(memberId), CoreErrorType.PROFILE_NOT_FOUND)
        return ProfileMapper.toDomain(
            entity,
            interestJobRoleIds = profileInterestFinder.findJobRoleIds(memberId),
            interestCompanyIds = profileInterestFinder.findCompanyIds(memberId),
        )
    }

    fun exists(memberId: UUID): Boolean = memberProfileRepository.existsByMemberIdAndDeletedAtIsNull(memberId)
}
