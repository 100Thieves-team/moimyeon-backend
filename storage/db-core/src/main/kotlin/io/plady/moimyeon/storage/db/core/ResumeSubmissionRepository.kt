package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface ResumeSubmissionRepository : JpaRepository<ResumeSubmissionEntity, Long> {
    fun findByRoomApplicationIdAndDeletedAtIsNull(roomApplicationId: Long): ResumeSubmissionEntity?
}
