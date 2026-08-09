package io.plady.moimyeon.core.api.facade

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.plady.moimyeon.core.domain.company.Company
import io.plady.moimyeon.core.domain.company.CompanyService
import io.plady.moimyeon.core.domain.jobposting.JobPostingSearchCondition
import io.plady.moimyeon.core.domain.jobposting.JobPostingSearchItem
import io.plady.moimyeon.core.domain.jobposting.JobPostingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JobPostingSearchFacadeTest {
    private val companyService: CompanyService = mockk()
    private val jobPostingService: JobPostingService = mockk()
    private lateinit var facade: JobPostingSearchFacade

    @BeforeEach
    fun setUp() {
        facade = JobPostingSearchFacade(companyService, jobPostingService)
    }

    @Test
    fun `companyId 가 주어지면 회사 검색을 건너뛴다`() {
        every { companyService.getCompanies(listOf(91221L)) } returns listOf(Company(91221L, "쿠팡"))
        every { companyService.getCompanies(emptySet()) } returns emptyList()
        every { jobPostingService.search(any()) } returns emptyList()

        val response = facade.search("백엔드", companyId = 91221L)

        verify(exactly = 0) { companyService.searchByPrefixes(any()) }
        assertThat(response.companies.map { it.name }).containsExactly("쿠팡")
    }

    @Test
    fun `companyId 가 주어지면 그 회사 밖의 공고를 찾지 않는다`() {
        val condition = slot<JobPostingSearchCondition>()
        every { companyService.getCompanies(listOf(91221L)) } returns listOf(Company(91221L, "쿠팡"))
        every { companyService.getCompanies(emptySet()) } returns emptyList()
        every { jobPostingService.search(capture(condition)) } returns emptyList()

        facade.search("개발", companyId = 91221L)

        assertThat(condition.captured.matchedCompanyIds).containsExactly(91221L)
        // 공고명 폴백은 회사를 보지 않는다. 켜두면 좁힌 회사 밖의 공고가 섞인다
        assertThat(condition.captured.tokens).isEmpty()
        assertThat(condition.captured.remainder).isEqualTo("개발")
    }

    @Test
    fun `검색어가 최소 길이 미만이면 조회를 시작하지 않는다`() {
        val response = facade.search("네", companyId = null)

        verify(exactly = 0) { companyService.searchByPrefixes(any()) }
        verify(exactly = 0) { jobPostingService.search(any<JobPostingSearchCondition>()) }
        assertThat(response.query).isEqualTo("네")
        assertThat(response.companies).isEmpty()
        assertThat(response.jobPostings).isEmpty()
    }

    @Test
    fun `companyId 가 있으면 검색어가 비어도 그 회사의 공고를 조회한다`() {
        val condition = slot<JobPostingSearchCondition>()
        every { companyService.getCompanies(listOf(91221L)) } returns listOf(Company(91221L, "쿠팡"))
        every { companyService.getCompanies(emptySet()) } returns emptyList()
        every { jobPostingService.search(capture(condition)) } returns emptyList()

        // 회사 행을 눌러 좁힌 직후의 상태. 회사 자체가 조회 조건이라 검색어가 없어도 목록이 채워져야 한다
        facade.search("", companyId = 91221L)

        assertThat(condition.captured.matchedCompanyIds).containsExactly(91221L)
        assertThat(condition.captured.remainder).isEmpty()
    }

    @Test
    fun `회사명을 채우지 못한 공고 행은 응답에서 제외한다`() {
        val kept = item(id = 1L, companyId = 100L)
        val dropped = item(id = 2L, companyId = 200L) // 회사가 폐기되어 이름 조회에서 빠진다
        every { companyService.searchByPrefixes(any()) } returns
            io.plady.moimyeon.core.domain.company.CompanyNameMatch.NONE
        every { jobPostingService.search(any<JobPostingSearchCondition>()) } returns listOf(kept, dropped)
        every { companyService.getCompanies(setOf(100L, 200L)) } returns listOf(Company(100L, "네이버"))

        val response = facade.search("백엔드", companyId = null)

        assertThat(response.jobPostings.map { it.jobPostingId }).containsExactly(1L)
    }

    private fun item(id: Long, companyId: Long) = JobPostingSearchItem(
        id = id,
        companyId = companyId,
        postingName = "백엔드 개발자",
        jobRoleId = null,
        jobRoleName = null,
        sourceUrl = null,
        verified = true,
        matchedByCompanyName = false,
    )
}
