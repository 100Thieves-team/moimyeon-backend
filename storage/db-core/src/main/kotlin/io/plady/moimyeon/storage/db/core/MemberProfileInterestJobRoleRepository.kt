package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MemberProfileInterestJobRoleRepository : JpaRepository<MemberProfileInterestJobRoleEntity, Long> {
    fun findByProfileIdAndDeletedAtIsNull(profileId: UUID): List<MemberProfileInterestJobRoleEntity>
}
