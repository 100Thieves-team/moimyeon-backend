package io.plady.moimyeon.core.domain.company

// 회사명 매치 결과. 어느 후보가 맞았는지를 함께 돌려주므로 호출자가 잔여 검색어를 계산할 수 있다.
data class CompanyNameMatch(
    val candidate: String,
    val companies: List<Company>,
) {
    val matched: Boolean get() = companies.isNotEmpty()

    companion object {
        val NONE = CompanyNameMatch("", emptyList())
    }
}
