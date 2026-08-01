package io.plady.moimyeon.core.domain.catalog

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.core.enums.SigunguLevel
import io.plady.moimyeon.storage.db.core.SidoEntity
import io.plady.moimyeon.storage.db.core.SidoRepository
import io.plady.moimyeon.storage.db.core.SigunguEntity
import io.plady.moimyeon.storage.db.core.SigunguRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Transactional
class RegionFinderIT(
    private val regionFinder: RegionFinder,
    private val sidoRepository: SidoRepository,
    private val sigunguRepository: SigunguRepository,
) : ContextTest() {
    @Test
    fun `유효한 시도와 시군구를 정렬하고 시도별로 묶어 반환한다`() {
        val firstSido = saveSido("98", "테스트광역시A", "테스트A", 30_000)
        saveSigungu(firstSido.id, "98002", "테스트구A2", 2)
        saveSigungu(firstSido.id, "98001", "테스트구A1", 1)
        saveSido("99", "테스트광역시B", "테스트B", 30_001)

        val regions = regionFinder.getRegions()

        assertThat(regions.takeLast(2).map { it.shortName }).containsExactly("테스트A", "테스트B")
        assertThat(regions.first { it.id == firstSido.id }.sigungus.map { it.name })
            .containsExactly("테스트구A1", "테스트구A2")
    }

    @Test
    fun `폐기된 시도와 시군구는 지역 카탈로그에서 제외한다`() {
        val now = LocalDateTime.of(2026, 8, 1, 0, 0)
        val sido = saveSido("98", "폐기테스트광역시", "폐기테스트", 30_000)
        val sigungu = saveSigungu(sido.id, "98001", "폐기테스트구", 1)
        sigungu.delete(now)
        sigunguRepository.flush()

        assertThat(regionFinder.getRegions().first { it.id == sido.id }.sigungus).isEmpty()

        sido.delete(now)
        sidoRepository.flush()

        assertThat(regionFinder.getRegions().map { it.id }).doesNotContain(sido.id)
    }

    private fun saveSido(code: String, name: String, shortName: String, sortOrder: Short): SidoEntity {
        return sidoRepository.saveAndFlush(SidoEntity(code, name, shortName, false, sortOrder))
    }

    private fun saveSigungu(sidoId: Long, code: String, name: String, sortOrder: Short): SigunguEntity {
        return sigunguRepository.saveAndFlush(SigunguEntity(sidoId, code, name, SigunguLevel.GU, sortOrder))
    }
}
