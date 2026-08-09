package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface SigunguRepository : JpaRepository<SigunguEntity, Long> {
    fun findByDeletedAtIsNullOrderBySidoIdAscSortOrderAsc(): List<SigunguEntity>

    fun existsByIdAndDeletedAtIsNull(id: Long): Boolean

    // 탐색 목록의 지역 표시명 배치 조회(MOI-383).
    fun findByIdInAndDeletedAtIsNull(ids: Collection<Long>): List<SigunguEntity>
}
