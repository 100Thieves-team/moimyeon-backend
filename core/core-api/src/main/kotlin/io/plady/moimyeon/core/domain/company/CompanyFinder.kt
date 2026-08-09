package io.plady.moimyeon.core.domain.company

import io.plady.moimyeon.storage.db.core.CompanyEntity
import io.plady.moimyeon.storage.db.core.CompanyRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

private const val SEARCH_LIMIT = 20
private const val PATTERN_SLOTS = 3

@Component
class CompanyFinder(
    private val companyRepository: CompanyRepository,
) {
    fun search(query: String): List<Company> = searchByPrefixes(listOf(CompanyNameNormalizer.normalize(query))).companies

    // 정규화 접두로 먼저 찾고, 0건일 때만 부분 일치로 다시 찾는다.
    // 부분 일치는 인덱스를 못 타 5만 행을 훑지만 접두가 실패했을 때만 돌므로 평소 경로는 그대로다.
    // 접두만 쓰면 `올리브영` 으로 `CJ올리브영` 을 못 찾는데, 정규화가 그룹 접두를 남기기 때문이다.
    fun searchByPrefixes(candidates: List<String>): CompanyNameMatch {
        val usable = candidates.filter { it.isNotBlank() }
        if (usable.isEmpty()) return CompanyNameMatch.NONE

        return longestMatch(usable, { "$it%" }, String::startsWith)
            ?: longestMatch(usable, { "%$it%" }, String::contains)
            ?: CompanyNameMatch.NONE
    }

    fun getByIds(companyIds: Collection<Long>): List<Company> {
        if (companyIds.isEmpty()) return emptyList()
        return companyRepository.findByIdInAndDeletedAtIsNull(companyIds).map { Company(it.id, it.nameKr) }
    }

    private fun longestMatch(
        candidates: List<String>,
        toPattern: (String) -> String,
        matches: (String, String) -> Boolean,
    ): CompanyNameMatch? {
        val slots = candidates.take(PATTERN_SLOTS)
        val found = companyRepository.searchByNormalizedPatterns(
            toPattern(slots[0]),
            toPattern(slots.getOrElse(1) { slots[0] }),
            toPattern(slots.getOrElse(2) { slots[0] }),
            PageRequest.of(0, SEARCH_LIMIT),
        )
        return slots.sortedByDescending { it.length }
            .firstNotNullOfOrNull { candidate ->
                found.filter { matches(it.nameNormalized.orEmpty(), candidate) }
                    .takeIf { it.isNotEmpty() }
                    ?.let { CompanyNameMatch(candidate, it.map(::toCompany)) }
            }
    }

    private fun toCompany(entity: CompanyEntity) = Company(entity.id, entity.nameKr)
}
