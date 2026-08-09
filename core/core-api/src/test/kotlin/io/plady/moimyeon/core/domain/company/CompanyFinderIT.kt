package io.plady.moimyeon.core.domain.company

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.storage.db.core.CompanyEntity
import io.plady.moimyeon.storage.db.core.CompanyRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Transactional
class CompanyFinderIT(
    private val companyFinder: CompanyFinder,
    private val companyRepository: CompanyRepository,
) : ContextTest() {
    @Test
    fun `검증 완료된 유효 회사만 이름순으로 최대 20건 검색한다`() {
        val companies = (0..21).map { index ->
            CompanyEntity(
                corpCode = null,
                nameKr = "검색대상%02d".format(index),
                nameNormalized = "검색대상%02d".format(index),
                verified = true,
            )
        }
        companyRepository.saveAllAndFlush(companies)
        companyRepository.saveAndFlush(
            CompanyEntity(corpCode = null, nameKr = "검색대상미검증", nameNormalized = "검색대상미검증", verified = false),
        )
        val deleted = companyRepository.saveAndFlush(
            CompanyEntity(corpCode = null, nameKr = "검색대상폐기", nameNormalized = "검색대상폐기", verified = true),
        )
        deleted.delete(LocalDateTime.of(2026, 8, 1, 0, 0))
        companyRepository.flush()

        val found = companyFinder.search("검색대상")

        assertThat(found).hasSize(20)
        assertThat(found.map { it.name }).containsExactlyElementsOf((0..19).map { "검색대상%02d".format(it) })
        assertThat(found.map { it.name }).doesNotContain("검색대상미검증", "검색대상폐기")
    }

    @Test
    fun `회사명 정규화 값의 접두 일치로 검색한다`() {
        persist("주식회사 카카오페이증권", "카카오페이증권")
        persist("카카오모빌리티", "카카오모빌리티")
        persist("올리브영로지스", "올리브영로지스")

        val found = companyFinder.searchByPrefixes(listOf("카카오"))

        assertThat(found.candidate).isEqualTo("카카오")
        assertThat(found.companies.map { it.name })
            .containsExactlyInAnyOrder("주식회사 카카오페이증권", "카카오모빌리티")
    }

    @Test
    fun `여러 접두 후보 중 가장 긴 매치를 고른다`() {
        persist("네이버", "네이버")
        persist("네이버파이낸셜", "네이버파이낸셜")
        persist("네이버클라우드", "네이버클라우드")

        // '네이버 파이낸셜 백엔드' 를 친 상태. 짧은 후보에서 멈추면 네이버클라우드까지 섞인다
        val found = companyFinder.searchByPrefixes(listOf("네이버", "네이버파이낸셜", "네이버파이낸셜백엔드"))

        assertThat(found.candidate).isEqualTo("네이버파이낸셜")
        assertThat(found.companies.map { it.name }).containsExactly("네이버파이낸셜")
    }

    @Test
    fun `접두로 찾지 못하면 부분 일치로 다시 찾는다`() {
        persist("CJ올리브영", "CJ올리브영")
        persist("씨제이올리브영주식회사", "씨제이올리브영")

        // 정규화가 CJ·씨제이 같은 그룹 접두를 남겨서 접두 매치로는 0건이다
        val found = companyFinder.searchByPrefixes(listOf("올리브영"))

        assertThat(found.candidate).isEqualTo("올리브영")
        assertThat(found.companies.map { it.name })
            .containsExactlyInAnyOrder("CJ올리브영", "씨제이올리브영주식회사")
    }

    @Test
    fun `회사 id 목록으로 유효한 회사 정보를 조회한다`() {
        val first = companyRepository.saveAndFlush(
            CompanyEntity(corpCode = null, nameKr = "테스트 회사 A", nameNormalized = "테스트 회사 A", verified = true),
        )
        val second = companyRepository.saveAndFlush(
            CompanyEntity(corpCode = null, nameKr = "테스트 회사 B", nameNormalized = "테스트 회사 B", verified = true),
        )

        val found = companyFinder.getByIds(listOf(first.id, second.id))

        assertThat(found.map { it.id }).containsExactlyInAnyOrder(first.id, second.id)
        assertThat(found.map { it.name }).containsExactlyInAnyOrder("테스트 회사 A", "테스트 회사 B")
    }

    @Test
    fun `기존 관심 회사는 검증이 해제된 뒤에도 id로 조회한다`() {
        val company = companyRepository.saveAndFlush(
            CompanyEntity(corpCode = null, nameKr = "검증 해제 회사", nameNormalized = "검증 해제 회사", verified = false),
        )

        assertThat(companyFinder.getByIds(listOf(company.id))).containsExactly(Company(company.id, "검증 해제 회사"))
    }

    private fun persist(nameKr: String, nameNormalized: String) = companyRepository.saveAndFlush(
        CompanyEntity(corpCode = null, nameKr = nameKr, nameNormalized = nameNormalized, verified = true),
    )
}
