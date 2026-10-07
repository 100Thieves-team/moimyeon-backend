package io.plady.moimyeon.storage.db.core

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.util.UUID

interface RoomGuestbookRepository : JpaRepository<RoomGuestbookEntity, Long> {
    fun findByRoomIdAndDeletedAtIsNull(roomId: UUID): RoomGuestbookEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateByRoomIdAndDeletedAtIsNull(roomId: UUID): RoomGuestbookEntity?
}
