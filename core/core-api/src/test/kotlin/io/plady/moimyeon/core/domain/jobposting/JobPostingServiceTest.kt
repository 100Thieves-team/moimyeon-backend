package io.plady.moimyeon.core.domain.jobposting

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.plady.moimyeon.core.domain.company.CompanyValidator
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class JobPostingServiceTest {
    private val jobPostingFinder = mockk<JobPostingFinder>()
    private val jobPostingManager = mockk<JobPostingManager>()
    private val openGraphClient = mockk<OpenGraphClient>()
    private val companyValidator = mockk<CompanyValidator>(relaxed = true)
    private val jobPostingSearchReader = mockk<JobPostingSearchReader>()
    private val service = JobPostingService(
        jobPostingFinder,
        jobPostingManager,
        openGraphClient,
        companyValidator,
        jobPostingSearchReader,
    )

    private val memberId: UUID = UUID.randomUUID()
    private val command = JobPostingCreationCommand(
        companyId = 43429L,
        url = "https://company.example.com/careers/12345",
        postingName = "프론트엔드 개발자",
    )

    @Test
    fun `생성은 회사 검증을 먼저 하고, 만든 뒤 저장된 공고를 재조회해 돌려준다`() {
        every { jobPostingManager.create(command, memberId) } returns 90101L
        every { jobPostingFinder.getById(90101L) } returns JobPosting(
            id = 90101L,
            companyId = 43429L,
            postingName = "프론트엔드 개발자",
            jobRoleId = null,
            jobRoleName = null,
            sourceUrl = command.url,
            verified = false,
        )

        val result = service.create(memberId, command)

        assertThat(result.id).isEqualTo(90101L)
        assertThat(result.verified).isFalse()
        verifyOrder {
            companyValidator.validateSelectable(listOf(43429L))
            jobPostingManager.create(command, memberId)
            jobPostingFinder.getById(90101L)
        }
    }

    @Test
    fun `존재하지 않는 회사면 생성으로 넘어가지 않고 E1303`() {
        every { companyValidator.validateSelectable(listOf(43429L)) } throws CoreException(CoreErrorType.COMPANY_NOT_FOUND)

        assertThatThrownBy { service.create(memberId, command) }
            .isInstanceOfSatisfying(CoreException::class.java) {
                assertThat(it.errorType).isEqualTo(CoreErrorType.COMPANY_NOT_FOUND)
            }

        verify(exactly = 0) { jobPostingManager.create(any(), any()) }
    }

    @Test
    fun `링크 메타데이터는 OG 클라이언트 결과를 그대로 전달한다`() {
        val metadata = LinkMetadata("공고명", "img", "desc", "https://company.example.com/careers/12345")
        every { openGraphClient.fetch("https://company.example.com/careers/12345") } returns metadata

        assertThat(service.fetchLinkMetadata("https://company.example.com/careers/12345")).isEqualTo(metadata)
    }
}
