package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface SigunguRepository : JpaRepository<SigunguEntity, Long> {
    fun findByRetiredAtIsNullOrderBySidoIdAscSortOrderAsc(): List<SigunguEntity>

    fun existsByIdAndRetiredAtIsNull(id: Long): Boolean
}
