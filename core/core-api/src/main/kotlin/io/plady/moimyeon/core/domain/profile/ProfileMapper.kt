package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.storage.db.core.MemberProfileEntity
import java.util.UUID

object ProfileMapper {
    // 관심 회사·직무는 별도 조인 엔티티라 프로필 행에 딸려 오지 않는다 — 따로 조회한 것을 함께 받는다.
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
        memberId = memberId,
        bio = content.bio,
        meetingPreference = content.meetingPreference,
        sigunguId = content.sigunguId,
    )
}
