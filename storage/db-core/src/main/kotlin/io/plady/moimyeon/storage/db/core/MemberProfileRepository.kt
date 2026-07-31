package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MemberProfileRepository : JpaRepository<MemberProfileEntity, UUID> {
    fun findByMemberIdAndDeletedAtIsNull(memberId: UUID): MemberProfileEntity?

    fun existsByMemberIdAndDeletedAtIsNull(memberId: UUID): Boolean
}
