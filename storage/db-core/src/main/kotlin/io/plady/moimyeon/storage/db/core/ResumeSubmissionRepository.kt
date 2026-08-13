package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ResumeSubmissionRepository : JpaRepository<ResumeSubmissionEntity, Long> {
    fun findByMemberIdAndSourceResumeIdInAndDeletedAtIsNullOrderBySubmittedAtDescIdDesc(
        memberId: UUID,
        sourceResumeIds: Collection<UUID>,
    ): List<ResumeSubmissionEntity>

    fun findByRoomApplicationIdAndDeletedAtIsNull(roomApplicationId: Long): ResumeSubmissionEntity?

    fun findByRoomApplicationIdInAndDeletedAtIsNull(roomApplicationIds: Collection<Long>): List<ResumeSubmissionEntity>

    fun findByRoomIdAndMemberIdAndDeletedAtIsNull(roomId: UUID, memberId: UUID): ResumeSubmissionEntity?

    // 룸 조건을 쿼리에 함께 건다 - 다른 룸의 제출 id 는 "없는 것"과 구분되지 않아야 존재 여부가 새지 않는다(MOI-414).
    fun findByIdAndRoomIdAndDeletedAtIsNull(id: Long, roomId: UUID): ResumeSubmissionEntity?

    // 룸 단위 일괄 조회. 참여자 명부가 참여자 수에 비례해 쿼리를 늘리지 않도록 한 번에 가져온다.
    fun findByRoomIdAndDeletedAtIsNull(roomId: UUID): List<ResumeSubmissionEntity>
}
