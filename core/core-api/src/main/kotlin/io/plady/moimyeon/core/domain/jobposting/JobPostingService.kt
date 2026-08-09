package io.plady.moimyeon.core.domain.jobposting

import io.plady.moimyeon.core.domain.company.CompanyValidator
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class JobPostingService(
    private val jobPostingFinder: JobPostingFinder,
    private val jobPostingManager: JobPostingManager,
    private val openGraphClient: OpenGraphClient,
    private val companyValidator: CompanyValidator,
    private val jobPostingSearchReader: JobPostingSearchReader,
) {
    fun search(companyId: Long, query: String): List<JobPosting> = jobPostingFinder.search(companyId, query)

    fun search(condition: JobPostingSearchCondition): List<JobPostingSearchItem> = jobPostingSearchReader.search(condition)

    // 탐색 목록의 공고 표시명 배치 조회(MOI-383).
    fun getRefs(ids: Collection<Long>): List<JobPostingRef> = jobPostingFinder.getRefsByIds(ids)

    // 링크의 OG 메타데이터로 공고명 후보·미리보기를 만든다(「룸 생성」 §4.1).
    // 외부 fetch 라 쓰기 트랜잭션 밖에서 수행한다(하드 룰: 외부 호출은 트랜잭션에 두지 않는다).
    fun fetchLinkMetadata(url: String): LinkMetadata = openGraphClient.fetch(url)

    // 링크로 공고를 즉시 생성한다. 회사가 카탈로그의 선택 가능한 회사인지 먼저 검증하고(쓰기 밖),
    // 실제 생성·멱등 처리는 Manager 트랜잭션이 맡는다. 응답은 저장된 값으로 재조립해 돌려준다.
    fun create(createdByMemberId: UUID, command: JobPostingCreationCommand): JobPosting {
        companyValidator.validateSelectable(listOf(command.companyId))
        val jobPostingId = jobPostingManager.create(command, createdByMemberId)
        return jobPostingFinder.getById(jobPostingId)
    }
}
