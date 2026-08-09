package io.plady.moimyeon.core.domain.company

// 검색어를 company.name_normalized 와 같은 규칙으로 정규화한다.
// 그 컬럼이 법인격과 공백을 뺀 값이라(`주식회사 카카오페이증권` → `카카오페이증권`),
// 검색어도 같은 규칙을 거쳐야 비교가 성립한다.
object CompanyNameNormalizer {
    private val LEGAL_FORMS = Regex("""\(주\)|\(유\)|㈜|주식회사|유한회사""")
    private val WHITESPACE = Regex("""\s+""")

    fun normalize(text: String): String = WHITESPACE.replace(LEGAL_FORMS.replace(text, ""), "")
}
