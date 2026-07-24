package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
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

    // 영속 엔티티를 조회해 필드를 갱신 (변경 감지)
    // save-merge 는 컬렉션 교체 시맨틱이 불명확해 사용 X
    @Transactional
    fun update(profile: MemberProfile): MemberProfile {
        val entity = requireFound(
            memberProfileRepository.findById(profile.memberId).orElse(null),
            CoreErrorType.PROFILE_NOT_FOUND,
        )
        entity.nickname = profile.nickname.value
        entity.jobRoleId = profile.jobRoleId
        entity.bio = profile.bio
        entity.meetingPreference = profile.meetingPreference
        entity.sigunguId = profile.sigunguId
        entity.interestCompanyIds.clear()
        entity.interestCompanyIds.addAll(profile.interestCompanyIds)
        return ProfileMapper.toDomain(entity)
    }
}
