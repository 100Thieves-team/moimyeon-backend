package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberProfileRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class ProfileManager(
    private val memberProfileRepository: MemberProfileRepository,
) {
    @Transactional
    fun append(memberId: UUID, content: ProfileContent): UUID {
        return memberProfileRepository.save(ProfileMapper.toEntity(memberId, content)).memberId
    }

    @Transactional
    fun update(memberId: UUID, content: ProfileContent): UUID {
        val entity = requireFound(
            memberProfileRepository.findById(memberId).orElse(null),
            CoreErrorType.PROFILE_NOT_FOUND,
        )
        entity.updateProfile(content.jobRoleId, content.bio, content.meetingPreference, content.sigunguId, content.interestCompanyIds)
        return entity.memberId
    }
}
