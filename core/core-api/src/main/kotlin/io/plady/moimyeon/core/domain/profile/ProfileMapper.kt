package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.storage.db.core.MemberProfileEntity

object ProfileMapper {
    fun toDomain(entity: MemberProfileEntity): MemberProfile = MemberProfile(
        memberId = entity.memberId,
        nickname = Nickname(entity.nickname),
        jobRoleId = entity.jobRoleId,
        bio = entity.bio,
        meetingPreference = entity.meetingPreference,
        sigunguId = entity.sigunguId,
        interestCompanyIds = entity.interestCompanyIds.toList(),
    )

    fun toEntity(profile: MemberProfile): MemberProfileEntity = MemberProfileEntity(
        memberId = profile.memberId,
        nickname = profile.nickname.value,
        jobRoleId = profile.jobRoleId,
        bio = profile.bio,
        meetingPreference = profile.meetingPreference,
        sigunguId = profile.sigunguId,
        interestCompanyIds = profile.interestCompanyIds.toMutableList(),
    )
}
