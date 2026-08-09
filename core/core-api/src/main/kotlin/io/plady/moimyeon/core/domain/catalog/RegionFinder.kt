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
    // 탐색 목록의 지역 표시명 배치 조회(MOI-383). 시군구명만으로는 화면에 쓸 수 없어 시도 약칭을 붙인다.
    // 시군구나 그 시도가 폐기됐으면 그 항목을 아예 돌려주지 않는다 — 표시명이 결측인 룸은 지역 없이 내려간다.
    @Transactional(readOnly = true)
    fun getRegionLabels(sigunguIds: Collection<Long>): List<RegionLabel> {
        if (sigunguIds.isEmpty()) return emptyList()

        val sigungus = sigunguRepository.findByIdInAndDeletedAtIsNull(sigunguIds)
        if (sigungus.isEmpty()) return emptyList()

        val sidoShortNames = sidoRepository.findByIdInAndDeletedAtIsNull(sigungus.map { it.sidoId }.toSet())
            .associate { it.id to it.shortName }

        return sigungus.mapNotNull { sigungu ->
            val shortName = sidoShortNames[sigungu.sidoId] ?: return@mapNotNull null
            RegionLabel(sigunguId = sigungu.id, label = "$shortName ${sigungu.name}")
        }
    }

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
