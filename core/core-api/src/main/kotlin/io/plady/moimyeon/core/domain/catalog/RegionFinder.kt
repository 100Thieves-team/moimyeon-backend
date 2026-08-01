package io.plady.moimyeon.core.domain.catalog

import io.plady.moimyeon.storage.db.core.SidoRepository
import io.plady.moimyeon.storage.db.core.SigunguRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class RegionFinder(
    private val sidoRepository: SidoRepository,
    private val sigunguRepository: SigunguRepository,
) {
    @Transactional(readOnly = true)
    fun getRegions(): List<Sido> {
        val sigungusBySido = sigunguRepository.findByDeletedAtIsNullOrderBySidoIdAscSortOrderAsc()
            .groupBy { it.sidoId }
        return sidoRepository.findByDeletedAtIsNullOrderBySortOrderAsc().map { sido ->
            Sido(
                id = sido.id,
                name = sido.name,
                shortName = sido.shortName,
                sigungus = sigungusBySido[sido.id].orEmpty().map { Sigungu(it.id, it.name) },
            )
        }
    }
}
