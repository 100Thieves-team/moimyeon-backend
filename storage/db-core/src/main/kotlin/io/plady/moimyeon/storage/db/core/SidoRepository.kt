package io.plady.moimyeon.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface SidoRepository : JpaRepository<SidoEntity, Long> {
    fun findByDeletedAtIsNullOrderBySortOrderAsc(): List<SidoEntity>

    // 탐색 목록의 지역 표시명("서울 강남구")은 시도 약칭과 시군구명을 합쳐 만든다.
    fun findByIdInAndDeletedAtIsNull(ids: Collection<Long>): List<SidoEntity>
}
