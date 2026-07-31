package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberProfileRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Component
class ProfileManager(
    private val memberProfileRepository: MemberProfileRepository,
    private val profileInterestManager: ProfileInterestManager,
) {
    @Transactional
    fun append(memberId: UUID, content: ProfileContent): UUID {
        val existing = memberProfileRepository.findForUpdateByMemberId(memberId)
        if (existing == null) {
            memberProfileRepository.save(ProfileMapper.toEntity(memberId, content))
        } else {
            requireBusiness(existing.isDeleted(), CoreErrorType.PROFILE_ALREADY_EXISTS)
            existing.active()
            existing.updateProfile(content.bio, content.meetingPreference, content.sigunguId)
        }
        replaceInterests(memberId, content)
        return memberId
    }

    @Transactional
    fun update(memberId: UUID, content: ProfileContent): UUID {
        val entity = requireFound(
            memberProfileRepository.findForUpdateByMemberId(memberId)?.takeIf { it.isActive() },
            CoreErrorType.PROFILE_NOT_FOUND,
        )
        entity.updateProfile(content.bio, content.meetingPreference, content.sigunguId)
        replaceInterests(memberId, content)
        return entity.memberId
    }

    private fun replaceInterests(memberId: UUID, content: ProfileContent) {
        profileInterestManager.replaceAll(
            memberId,
            content.interestCompanyIds,
            content.interestJobRoleIds,
            LocalDateTime.now(),
        )
    }
}
