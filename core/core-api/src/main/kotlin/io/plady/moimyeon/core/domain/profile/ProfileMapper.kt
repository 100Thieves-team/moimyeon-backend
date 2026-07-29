package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.storage.db.core.MemberProfileEntity
import java.util.UUID

object ProfileMapper {
    fun toDomain(entity: MemberProfileEntity): MemberProfile = MemberProfile(
        memberId = entity.memberId,
        jobRoleId = entity.jobRoleId,
        bio = entity.bio,
        meetingPreference = entity.meetingPreference,
        sigunguId = entity.sigunguId,
        interestCompanyIds = entity.interestCompanyIds.toList(),
    )

    fun toEntity(memberId: UUID, content: ProfileContent): MemberProfileEntity = MemberProfileEntity(
        memberId = memberId,
        jobRoleId = content.jobRoleId,
        bio = content.bio,
        meetingPreference = content.meetingPreference,
        sigunguId = content.sigunguId,
        interestCompanyIds = content.interestCompanyIds.toMutableList(),
    )
}
