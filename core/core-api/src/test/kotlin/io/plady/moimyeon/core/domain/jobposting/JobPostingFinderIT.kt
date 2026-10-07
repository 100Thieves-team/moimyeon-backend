package io.plady.moimyeon.core.domain.jobposting

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.storage.db.core.JobPostingEntity
import io.plady.moimyeon.storage.db.core.JobPostingRepository
import io.plady.moimyeon.storage.db.core.JobPostingRoleEntity
import io.plady.moimyeon.storage.db.core.JobPostingRoleRepository
import io.plady.moimyeon.storage.db.core.JobRoleEntity
import io.plady.moimyeon.storage.db.core.JobRoleRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Transactional
class JobPostingFinderIT(
    private val jobPostingFinder: JobPostingFinder,
    private val jobPostingRepository: JobPostingRepository,
    private val jobPostingRoleRepository: JobPostingRoleRepository,
    private val jobRoleRepository: JobRoleRepository,
) : ContextTest() {
    private var uidSeq = 0

    @Test
    fun `회사에 속한 활성 공고만 공고명으로 검색해 최신순으로 반환한다`() {
        val companyId = 1001L
        val other = 2002L

        val recent = savePosting(companyId, "백엔드 개발자 (정산)", isOpen = true, postedAt = day(10))
        val older = savePosting(companyId, "백엔드 개발자 (결제)", isOpen = true, postedAt = day(3))
        savePosting(companyId, "프로덕트 디자이너", isOpen = true, postedAt = day(9)) // 공고명 불일치
        savePosting(companyId, "백엔드 마감 공고", isOpen = false, postedAt = day(8)) // 비활성
        savePosting(other, "백엔드 개발자 (외부)", isOpen = true, postedAt = day(9)) // 다른 회사
        savePosting(companyId, "백엔드 폐기 공고", isOpen = true, postedAt = day(9)).also {
            it.delete(day(11))
            jobPostingRepository.flush()
        }

        val found = jobPostingFinder.search(companyId, "백엔드 개발자")

        assertThat(found.map { it.id }).containsExactly(recent.id, older.id)
        assertThat(found.map { it.companyId }).containsOnly(companyId)
        assertThat(found.map { it.postingName }).containsExactly("백엔드 개발자 (정산)", "백엔드 개발자 (결제)")
    }

    @Test
    fun `검색어가 없으면 회사의 활성 공고 전체를 반환한다`() {
        val companyId = 3003L
        savePosting(companyId, "백엔드 개발자", isOpen = true, postedAt = day(2))
        savePosting(companyId, "프론트엔드 개발자", isOpen = true, postedAt = day(1))

        val found = jobPostingFinder.search(companyId, "")

        assertThat(found).hasSize(2)
    }

    @Test
    fun `공고에 매핑된 대표 직무는 가장 작은 직무 id 를 쓰고, 매핑이 없으면 null 이다`() {
        val companyId = 4004L
        val lowRole = jobRoleRepository.saveAndFlush(JobRoleEntity(jobGroupId = 1L, code = "BE", displayName = "백엔드 개발", sortOrder = 1))
        val highRole = jobRoleRepository.saveAndFlush(JobRoleEntity(jobGroupId = 1L, code = "FE", displayName = "프론트엔드 개발", sortOrder = 2))

        val mapped = savePosting(companyId, "풀스택 공고", isOpen = true, postedAt = day(2))
        val unmapped = savePosting(companyId, "직무 미매핑 공고", isOpen = true, postedAt = day(1))
        jobPostingRoleRepository.saveAllAndFlush(
            listOf(
                JobPostingRoleEntity(jobPostingId = mapped.id, jobRoleId = highRole.id),
                JobPostingRoleEntity(jobPostingId = mapped.id, jobRoleId = lowRole.id),
            ),
        )

        val found = jobPostingFinder.search(companyId, "").associateBy { it.id }

        assertThat(found.getValue(mapped.id).jobRoleId).isEqualTo(lowRole.id)
        assertThat(found.getValue(mapped.id).jobRoleName).isEqualTo("백엔드 개발")
        assertThat(found.getValue(unmapped.id).jobRoleId).isNull()
        assertThat(found.getValue(unmapped.id).jobRoleName).isNull()
    }

    private fun savePosting(companyId: Long, title: String, isOpen: Boolean, postedAt: LocalDateTime): JobPostingEntity = jobPostingRepository.saveAndFlush(
        JobPostingEntity(
            sourceUid = "uid-${uidSeq++}",
            companyId = companyId,
            title = title,
            isOpen = isOpen,
            sourceUrl = "https://example.com/careers/$uidSeq",
            postedAt = postedAt,
            verified = true,
        ),
    )

    private fun day(dayOfMonth: Int): LocalDateTime = LocalDateTime.of(2026, 8, dayOfMonth, 0, 0)
}
