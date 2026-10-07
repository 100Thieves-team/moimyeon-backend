package io.plady.moimyeon.core.domain.jobposting

import io.plady.moimyeon.core.domain.company.CompanyNameNormalizer

// 통합 검색어를 회사로 확정할 부분과 나머지로 나누는 규칙을 갖는다(MOI-390 §3-F).
// 경계는 공백이다. `네이버백엔드` 처럼 붙여 친 입력은 분해되지 않는다(D22, 알려진 한계).
class SearchKeyword private constructor(
    val raw: String,
    val tokens: List<String>,
) {
    val normalized: String get() = CompanyNameNormalizer.normalize(raw)

    val searchable: Boolean get() = normalized.length >= MIN_LENGTH

    // 앞 토큰부터 누적해 정규화한 회사 후보. 회사명이 여러 토큰으로 쓰인 경우
    // (`네이버 파이낸셜`)를 잡으려면 누적이 필요하다. 개수는 쿼리 절 수에 묶여 있다.
    fun companyPrefixCandidates(): List<String> = (1..minOf(tokens.size, CANDIDATE_LIMIT))
        .map { CompanyNameNormalizer.normalize(tokens.take(it).joinToString(" ")) }
        .filter { it.isNotBlank() }

    // 회사가 소비하고 남은 검색어. 정규화하지 않는다 —
    // job_posting.title 에는 정규화 컬럼이 없어 원본끼리 맞춰야 매치된다.
    fun remainderAfter(consumedTokenCount: Int): String = tokens.drop(consumedTokenCount).joinToString(" ")

    companion object {
        const val MIN_LENGTH = 2
        const val MAX_LENGTH = 50
        const val CANDIDATE_LIMIT = 3

        fun of(raw: String): SearchKeyword {
            val trimmed = raw.trim()
            return SearchKeyword(trimmed, trimmed.split(WHITESPACE).filter { it.isNotBlank() })
        }

        private val WHITESPACE = Regex("""\s+""")
    }
}
