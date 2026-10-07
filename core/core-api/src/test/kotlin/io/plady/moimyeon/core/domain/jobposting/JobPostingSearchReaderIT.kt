package io.plady.moimyeon.core.domain.jobposting

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.storage.db.core.JobPostingEntity
import io.plady.moimyeon.storage.db.core.JobPostingRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

// 검색어 분해(SearchKeyword)는 아직 붙이지 않고 원시 파라미터로 호출한다.
@Transactional
class JobPostingSearchReaderIT(
    private val jobPostingSearchReader: JobPostingSearchReader,
    private val jobPostingRepository: JobPostingRepository,
) : ContextTest() {
    private var uidSeq = 0

    @Test
    fun `회사 매치를 앞세우고 같은 그룹 안에서는 최신 공고 순으로 정렬한다`() {
        val matched = 9001L
        val other = 9002L
        val recent = savePosting(matched, "백엔드 개발자 (정산)", postedAt = day(10))
        val older = savePosting(matched, "프로덕트 디자이너", postedAt = day(3))
        val byTitle = savePosting(other, "백엔드 엔지니어", postedAt = day(20)) // 가장 최신이지만 rank 1

        val found = jobPostingSearchReader.search(condition(matched, tokens = listOf("백엔드")))

        assertThat(found.map { it.id }).containsExactly(recent.id, older.id, byTitle.id)
    }

    @Test
    fun `두 분기에 모두 걸린 공고는 한 번만 나온다`() {
        val companyId = 9003L
        val posting = savePosting(companyId, "백엔드 개발자", postedAt = day(5))

        val found = jobPostingSearchReader.search(condition(companyId, tokens = listOf("백엔드")))

        assertThat(found.map { it.id }).containsExactly(posting.id)
        assertThat(found.single().matchedByCompanyName).isTrue()
    }

    @Test
    fun `회사가 지정되지 않은 공고는 결과에서 제외한다`() {
        val kept = savePosting(9004L, "백엔드 개발자", postedAt = day(5))
        val orphan = savePosting(companyId = null, title = "백엔드 개발자 (회사 미매칭)", postedAt = day(6))

        val found = jobPostingSearchReader.search(condition(tokens = listOf("백엔드")))

        assertThat(found.map { it.id }).containsExactly(kept.id)
        assertThat(found.map { it.id }).doesNotContain(orphan.id)
    }

    @Test
    fun `미검증 공고는 결과에 포함한다`() {
        val companyId = 9005L
        val verified = savePosting(companyId, "백엔드 개발자 (검증)", postedAt = day(5), verified = true)
        val unverified = savePosting(companyId, "백엔드 개발자 (링크 생성)", postedAt = day(6), verified = false)

        val found = jobPostingSearchReader.search(condition(companyId, tokens = listOf("백엔드")))

        assertThat(found.map { it.id }).containsExactlyInAnyOrder(verified.id, unverified.id)
    }

    @Test
    fun `분기별로 조회한 결과를 병합해도 상한을 넘지 않는다`() {
        val matched = 9006L
        val other = 9007L
        repeat(JobPostingSearchReader.SEARCH_LIMIT) { savePosting(matched, "백엔드 회사매치 $it", postedAt = day(10)) }
        repeat(JobPostingSearchReader.SEARCH_LIMIT) { savePosting(other, "백엔드 공고명매치 $it", postedAt = day(10)) }

        val found = jobPostingSearchReader.search(condition(matched, tokens = listOf("백엔드")))

        // 분기별로만 자르면 40건이 된다
        assertThat(found).hasSize(JobPostingSearchReader.SEARCH_LIMIT)
    }

    @Test
    fun `잔여 검색어가 있으면 그 회사의 공고를 공고명으로 좁힌다`() {
        val companyId = 9008L
        val backend = savePosting(companyId, "백엔드 개발자", postedAt = day(5))
        val frontend = savePosting(companyId, "프론트엔드 개발자", postedAt = day(6))

        val found = jobPostingSearchReader.search(
            condition(companyId, remainder = "백엔드", tokens = listOf("네이버", "백엔드")),
        )

        assertThat(found.map { it.id }).containsExactly(backend.id)
        assertThat(found.map { it.id }).doesNotContain(frontend.id)
    }

    @Test
    fun `잔여 검색어가 없으면 그 회사의 공고를 모두 반환한다`() {
        val companyId = 9009L
        val backend = savePosting(companyId, "백엔드 개발자", postedAt = day(5))
        val designer = savePosting(companyId, "프로덕트 디자이너", postedAt = day(6))

        val found = jobPostingSearchReader.search(condition(companyId, tokens = listOf("네이버")))

        assertThat(found.map { it.id }).containsExactlyInAnyOrder(backend.id, designer.id)
    }

    @Test
    fun `잔여 검색어가 공고명보다 길어도 매치된다`() {
        val companyId = 9010L
        val posting = savePosting(companyId, "백엔드 개발자", postedAt = day(5))

        val found = jobPostingSearchReader.search(
            condition(companyId, remainder = "백엔드 개발자 채용", tokens = listOf("네이버", "백엔드", "개발자", "채용")),
        )

        assertThat(found.map { it.id }).containsExactly(posting.id)
    }

    @Test
    fun `공고명 폴백은 토큰을 AND 로 걸어 순서와 무관하게 매치한다`() {
        val companyId = 9011L
        val scattered = savePosting(companyId, "[네이버 계열사] 백엔드 엔지니어", postedAt = day(5))
        val partial = savePosting(companyId, "백엔드 엔지니어", postedAt = day(6)) // '네이버' 없음

        val found = jobPostingSearchReader.search(condition(tokens = listOf("백엔드", "네이버")))

        assertThat(found.map { it.id }).containsExactly(scattered.id)
        assertThat(found.map { it.id }).doesNotContain(partial.id)
    }

    private fun condition(
        vararg matchedCompanyIds: Long,
        remainder: String = "",
        tokens: List<String>,
    ) = JobPostingSearchCondition(
        matchedCompanyIds = matchedCompanyIds.toList(),
        remainder = remainder,
        tokens = tokens,
    )

    private fun savePosting(
        companyId: Long?,
        title: String,
        postedAt: LocalDateTime,
        verified: Boolean = true,
    ): JobPostingEntity = jobPostingRepository.saveAndFlush(
        JobPostingEntity(
            sourceUid = "search-uid-${uidSeq++}",
            companyId = companyId,
            title = title,
            isOpen = true,
            sourceUrl = "https://example.com/careers/$uidSeq",
            postedAt = postedAt,
            verified = verified,
        ),
    )

    private fun day(dayOfMonth: Int): LocalDateTime = LocalDateTime.of(2026, 8, dayOfMonth, 0, 0)
}
