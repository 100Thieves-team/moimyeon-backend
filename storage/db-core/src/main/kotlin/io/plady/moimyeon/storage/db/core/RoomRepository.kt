package io.plady.moimyeon.storage.db.core

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RoomRepository : JpaRepository<RoomEntity, UUID> {
    // 수락 시 룸 행에 쓰기 잠금을 걸어 동시 수락을 직렬화한다(「룸 참여」 §4.4 마지막 자리 1건만 성공).
    // 잠금을 잡은 뒤 현재 인원을 세고 정원을 확정하므로, 경쟁하는 수락 트랜잭션은 순서대로만 통과한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoomEntity r where r.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): RoomEntity?
}
