package io.plady.moimyeon.storage.db.core

import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@MappedSuperclass
abstract class AbstractEntity {
    private var deletedAt: LocalDateTime? = null

    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.MIN

    @UpdateTimestamp
    val updatedAt: LocalDateTime = LocalDateTime.MIN

    fun active() {
        deletedAt = null
    }

    fun isActive(): Boolean {
        return deletedAt == null
    }

    fun delete(now: LocalDateTime) {
        if (deletedAt == null) deletedAt = now
    }

    fun isDeleted(): Boolean {
        return deletedAt != null
    }
}
