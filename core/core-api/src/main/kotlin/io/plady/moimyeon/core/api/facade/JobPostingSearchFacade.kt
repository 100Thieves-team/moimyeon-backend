package io.plady.moimyeon.core.api.facade

import io.plady.moimyeon.core.api.controller.v1.response.JobPostingSearchResponse
import io.plady.moimyeon.core.domain.company.CompanyNameMatch
import io.plady.moimyeon.core.domain.company.CompanyService
import io.plady.moimyeon.core.domain.jobposting.JobPostingSearchCondition
import io.plady.moimyeon.core.domain.jobposting.JobPostingService
import io.plady.moimyeon.core.domain.jobposting.SearchKeyword
import org.springframework.stereotype.Component

@Component
class JobPostingSearchFacade(
    private val companyService: CompanyService,
    private val jobPostingService: JobPostingService,
) {
    fun search(rawQuery: String, companyId: Long?): JobPostingSearchResponse {
        val keyword = SearchKeyword.of(rawQuery)
        // 타이핑 중 호출이라 입력 중간 상태는 에러가 아니다. 조회를 시작하지 않고 빈 결과를 돌려준다.
        if (!keyword.searchable) return JobPostingSearchResponse.empty(rawQuery)

        val candidates = keyword.companyPrefixCandidates()
        // companyId 가 오면 회사는 이미 확정된 상태다. 화면에서 회사 행을 눌러 좁힌 경로.
        val match = if (companyId == null) companyService.searchByPrefixes(candidates) else CompanyNameMatch.NONE
        val consumedTokens = if (match.matched) candidates.indexOf(match.candidate) + 1 else 0

        val condition = JobPostingSearchCondition(
            matchedCompanyIds = companyId?.let(::listOf) ?: match.companies.map { it.id },
            remainder = keyword.remainderAfter(consumedTokens),
            tokens = keyword.tokens,
        )
        val jobPostings = jobPostingService.search(condition)

        val companyRows = companyId?.let { companyService.getCompanies(listOf(it)) } ?: match.companies
        val companyNames = companyService.getCompanies(jobPostings.map { it.companyId }.toSet())

        return JobPostingSearchResponse.of(rawQuery, companyRows, jobPostings, companyNames.associateBy { it.id })
    }
}
