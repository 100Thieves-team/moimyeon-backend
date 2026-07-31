package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.storage.db.core.MemberProfileEntity
import java.util.UUID

object ProfileMapper {
    fun toDomain(
        entity: MemberProfileEntity,
        interestJobRoleIds: List<Long>,
        interestCompanyIds: List<Long>,
    ): MemberProfile = MemberProfile(
        memberId = entity.memberId,
        bio = entity.bio,
        meetingPreference = entity.meetingPreference,
        sigunguId = entity.sigunguId,
        interestJobRoleIds = interestJobRoleIds,
        interestCompanyIds = interestCompanyIds,
    )

    fun toEntity(memberId: UUID, content: ProfileContent): MemberProfileEntity = MemberProfileEntity(
        id = UUID.randomUUID(),
        memberId = memberId,
        bio = content.bio,
        meetingPreference = content.meetingPreference,
        sigunguId = content.sigunguId,
    )
}
