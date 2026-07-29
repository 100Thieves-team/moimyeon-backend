package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface SigunguRepository : JpaRepository<SigunguEntity, Long> {
    fun findByDeletedAtIsNullOrderBySidoIdAscSortOrderAsc(): List<SigunguEntity>

    fun existsByIdAndDeletedAtIsNull(id: Long): Boolean
}
