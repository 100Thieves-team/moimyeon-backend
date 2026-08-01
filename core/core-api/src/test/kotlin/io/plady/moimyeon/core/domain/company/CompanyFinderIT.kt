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
}
