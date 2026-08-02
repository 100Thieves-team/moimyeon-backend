package io.plady.moimyeon.core.domain.jobposting

import io.plady.moimyeon.ContextTest
import io.plady.moimyeon.storage.db.core.JobPostingRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Transactional
class JobPostingManagerIT(
    private val jobPostingManager: JobPostingManager,
    private val jobPostingRepository: JobPostingRepository,
) : ContextTest() {
    private val companyId = 43429L
    private val memberId: UUID = UUID.randomUUID()

    @Test
    fun `링크로 공고를 생성하면 verified=false·is_open=true·생성자·출처가 저장된다`() {
        val command = JobPostingCreationCommand(
            companyId = companyId,
            url = "https://company.example.com/careers/12345",
            postingName = "프론트엔드 개발자 (결제플랫폼)",
        )

        val id = jobPostingManager.create(command, memberId)

        val saved = jobPostingRepository.findByIdAndDeletedAtIsNull(id)!!
        assertThat(saved.verified).isFalse()
        assertThat(saved.isOpen).isTrue()
        assertThat(saved.companyId).isEqualTo(companyId)
        assertThat(saved.title).isEqualTo("프론트엔드 개발자 (결제플랫폼)")
        assertThat(saved.sourceUrl).isEqualTo("https://company.example.com/careers/12345")
        assertThat(saved.createdByMemberId).isEqualTo(memberId)
        assertThat(saved.sourceUid).startsWith("lnk:")
    }

    @Test
    fun `생성한 공고는 그 회사의 룸 생성에 바로 선택 가능하다`() {
        val command = JobPostingCreationCommand(
            companyId = companyId,
            url = "https://company.example.com/careers/room-ready",
            postingName = "백엔드 개발자 (정산)",
        )

        val id = jobPostingManager.create(command, memberId)

        // 룸 생성 검증 경로가 쓰는 조건(회사 소속·활성·미폐기)을 그대로 만족해야 한다.
        assertThat(jobPostingRepository.existsByIdAndCompanyIdAndIsOpenTrueAndDeletedAtIsNull(id, companyId)).isTrue()
    }

    @Test
    fun `같은 URL 재요청은 새로 만들지 않고 같은 공고를 돌려준다`() {
        val command = JobPostingCreationCommand(
            companyId = companyId,
            url = "https://company.example.com/careers/dup",
            postingName = "프론트엔드 개발자",
        )

        val first = jobPostingManager.create(command, memberId)
        // 회사·공고명을 바꿔 다시 보내도 URL 이 같으면 최초 공고를 재사용한다(멱등).
        val second = jobPostingManager.create(command.copy(companyId = 99999L, postingName = "다른 이름"), UUID.randomUUID())

        assertThat(second).isEqualTo(first)
        val saved = jobPostingRepository.findByIdAndDeletedAtIsNull(first)!!
        assertThat(saved.companyId).isEqualTo(companyId)
        assertThat(saved.title).isEqualTo("프론트엔드 개발자")
    }

    @Test
    fun `다른 URL 은 각각 다른 공고로 생성된다`() {
        val a = jobPostingManager.create(
            JobPostingCreationCommand(companyId, "https://company.example.com/careers/a", "공고 A"),
            memberId,
        )
        val b = jobPostingManager.create(
            JobPostingCreationCommand(companyId, "https://company.example.com/careers/b", "공고 B"),
            memberId,
        )

        assertThat(a).isNotEqualTo(b)
    }
}
