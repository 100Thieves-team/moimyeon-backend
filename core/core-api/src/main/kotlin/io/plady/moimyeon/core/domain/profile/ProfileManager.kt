package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.storage.db.core.MemberProfileRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProfileManager(
    private val memberProfileRepository: MemberProfileRepository,
) {
    @Transactional
    fun append(profile: MemberProfile): MemberProfile {
        return ProfileMapper.toDomain(memberProfileRepository.save(ProfileMapper.toEntity(profile)))
    }
}
