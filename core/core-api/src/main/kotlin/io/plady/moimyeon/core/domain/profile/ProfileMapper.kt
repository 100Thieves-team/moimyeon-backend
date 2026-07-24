package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.storage.db.core.MemberProfileEntity

object ProfileMapper {
    fun toDomain(entity: MemberProfileEntity): MemberProfile = MemberProfile(
        memberId = entity.memberId,
        nickname = Nickname(entity.nickname),
        jobTitle = entity.jobTitle,
        bio = entity.bio,
        meetingPreference = entity.meetingPreference,
        region = entity.region,
    )

    fun toEntity(profile: MemberProfile): MemberProfileEntity = MemberProfileEntity(
        memberId = profile.memberId,
        nickname = profile.nickname.value,
        jobTitle = profile.jobTitle,
        bio = profile.bio,
        meetingPreference = profile.meetingPreference,
        region = profile.region,
    )
}
