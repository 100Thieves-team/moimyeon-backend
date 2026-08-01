package io.plady.moimyeon.core.domain.resume

import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ResumeEntity
import io.plady.moimyeon.storage.db.core.ResumeRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class ResumeRegistrar(
    private val memberRepository: MemberRepository,
    private val resumeRepository: ResumeRepository,
) {
    fun validateCapacity(memberId: UUID) {
        requireCapacity(resumeRepository.countByMemberIdAndArchivedAtIsNullAndDeletedAtIsNull(memberId))
    }

    @Transactional
    fun register(memberId: UUID, name: String, file: ResumeFile): UUID {
        // 회원 행 잠금이 같은 회원의 동시 등록을 직렬화한다. 단순 조회가 아니라
        // 최대 개수와 첫 기본 이력서 판정을 한 커밋에서 확정하기 위한 잠금이다.
        requireFound(memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId), CoreErrorType.MEMBER_NOT_FOUND)

        val resumeCount = resumeRepository.countByMemberIdAndArchivedAtIsNullAndDeletedAtIsNull(memberId)
        requireCapacity(resumeCount)

        val resumeId = resumeRepository.save(
            ResumeEntity(
                id = UUID.randomUUID(),
                memberId = memberId,
                name = name,
                fileKey = file.key,
                originalName = file.originalName,
                sizeBytes = file.sizeBytes,
                contentType = file.contentType,
                summaryStatus = ResumeSummaryStatus.PROCESSING,
                isDefault = resumeCount == 0L,
            ),
        ).id

        return resumeId
    }

    private fun requireCapacity(resumeCount: Long) {
        requireBusiness(resumeCount < RESUME_VAULT_MAX_COUNT, CoreErrorType.RESUME_LIMIT_EXCEEDED)
    }
}
