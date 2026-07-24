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
    fun findActiveSidos(): List<Sido> {
        val sigungusBySido = sigunguRepository.findByRetiredAtIsNullOrderBySidoIdAscSortOrderAsc()
            .groupBy { it.sidoId }
        return sidoRepository.findByRetiredAtIsNullOrderBySortOrderAsc().map { sido ->
            Sido(
                id = sido.id,
                name = sido.name,
                shortName = sido.shortName,
                sigungus = sigungusBySido[sido.id].orEmpty().map { Sigungu(it.id, it.name) },
            )
        }
    }

    @Transactional(readOnly = true)
    fun existsActiveSigungu(sigunguId: Long): Boolean = sigunguRepository.existsByIdAndRetiredAtIsNull(sigunguId)
}
