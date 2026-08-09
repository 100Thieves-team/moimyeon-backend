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
        // companyId 가 있으면 회사 자체가 조회 조건이므로 검색어가 비어도 그 회사의 공고를 채운다.
        if (companyId == null && !keyword.searchable) return JobPostingSearchResponse.empty(rawQuery)

        val candidates = keyword.companyPrefixCandidates()
        // companyId 가 오면 회사는 이미 확정된 상태다. 화면에서 회사 행을 눌러 좁힌 경로.
        val match = if (companyId == null) companyService.searchByPrefixes(candidates) else CompanyNameMatch.NONE
        val consumedTokens = if (match.matched) candidates.indexOf(match.candidate) + 1 else 0

        val condition = JobPostingSearchCondition(
            matchedCompanyIds = companyId?.let(::listOf) ?: match.companies.map { it.id },
            remainder = keyword.remainderAfter(consumedTokens),
            // 좁히기에서는 공고명 폴백을 끈다. 그 폴백은 회사를 보지 않아서
            // 켜두면 좁힌 회사 밖의 공고가 함께 나온다.
            tokens = if (companyId == null) keyword.tokens else emptyList(),
        )
        val jobPostings = jobPostingService.search(condition)

        val companyRows = companyId?.let { companyService.getCompanies(listOf(it)) } ?: match.companies
        val companyNames = companyService.getCompanies(jobPostings.map { it.companyId }.toSet())

        return JobPostingSearchResponse.of(rawQuery, companyRows, jobPostings, companyNames.associateBy { it.id })
    }
}
