package io.plady.moimyeon.core.domain.jobposting

import io.plady.moimyeon.storage.db.core.JobPostingEntity
import io.plady.moimyeon.storage.db.core.JobPostingRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

// 회사명 매치 분기(rank 0)와 공고명 매치 분기(rank 1)를 각각 조회해 병합·dedup·랭킹까지 한다.
// 단건 조회의 JobPostingFinder 와 책임이 달라 분리한다.
@Component
class JobPostingSearchReader(
    private val jobPostingRepository: JobPostingRepository,
) {
    fun search(condition: JobPostingSearchCondition): List<JobPostingSearchItem> {
        val limit = PageRequest.of(0, SEARCH_LIMIT)

        val byCompanyName = if (condition.matchedCompanyIds.isEmpty()) {
            emptyList()
        } else {
            jobPostingRepository.searchByCompanyIds(
                condition.matchedCompanyIds,
                containsPattern(condition.remainder),
                condition.remainder,
                limit,
            )
        }

        val tokens = condition.tokens.filter { it.isNotBlank() }.take(TOKEN_LIMIT)
        val byTitle = if (tokens.isEmpty()) {
            emptyList()
        } else {
            jobPostingRepository.searchByTitleTokens(
                containsPattern(tokens.getOrNull(0)),
                containsPattern(tokens.getOrNull(1)),
                containsPattern(tokens.getOrNull(2)),
                limit,
            )
        }

        // 두 분기가 각각 정렬된 채로 나오므로 이어붙이면 rank 0 그룹, rank 1 그룹 순서가 그대로 유지된다.
        // 여기서 다시 정렬하면 회사 매치 우선순위가 깨진다.
        return (byCompanyName.map { it to true } + byTitle.map { it to false })
            .distinctBy { (posting, _) -> posting.id }
            .take(SEARCH_LIMIT)
            .map { (posting, matchedByCompanyName) -> toItem(posting, matchedByCompanyName) }
    }

    // 빈 값은 '%' 가 되어 조건이 항상 참이 된다. 덕분에 "좁히기"와 "전체"를 쿼리 하나가 처리한다.
    private fun containsPattern(value: String?): String = if (value.isNullOrBlank()) "%" else "%$value%"

    private fun toItem(posting: JobPostingEntity, matchedByCompanyName: Boolean) = JobPostingSearchItem(
        id = posting.id,
        companyId = checkNotNull(posting.companyId) { "회사 없는 공고는 조회에서 제외된다. jobPostingId=${posting.id}" },
        postingName = posting.title,
        // 대표 직무는 이 단계의 관심사가 아니다. JobPostingFinder 의 조립 로직을 추출하면서 채운다.
        jobRoleId = null,
        jobRoleName = null,
        sourceUrl = posting.sourceUrl,
        verified = posting.verified,
        matchedByCompanyName = matchedByCompanyName,
    )

    companion object {
        // 클라이언트가 키울 수 없는 서버 고정 상한.
        const val SEARCH_LIMIT = 20

        // 쿼리 절 개수가 고정이라 필요한 상한(D21).
        const val TOKEN_LIMIT = 3
    }
}
