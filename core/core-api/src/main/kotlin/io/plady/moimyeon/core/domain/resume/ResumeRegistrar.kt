package io.plady.moimyeon.core.domain.resume

import io.plady.moimyeon.core.enums.ResumeSummaryStatus
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.CoreException
import io.plady.moimyeon.core.support.error.requireBusiness
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
        val resumeCount = resumeRepository.countByMemberIdAndDeletedAtIsNull(memberId)
        requireBusiness(
            resumeCount < MAX_RESUME_COUNT,
            CoreErrorType.RESUME_LIMIT_EXCEEDED,
        )
    }

    @Transactional
    fun register(memberId: UUID, resume: NewResume): UUID {
        lockMember(memberId)

        val resumeCount = resumeRepository.countByMemberIdAndDeletedAtIsNull(memberId)
        requireBusiness(resumeCount < MAX_RESUME_COUNT, CoreErrorType.RESUME_LIMIT_EXCEEDED)

        val resumeId = resumeRepository.save(
            ResumeEntity(
                id = UUID.randomUUID(),
                memberId = memberId,
                name = resume.name,
                fileKey = resume.file.key,
                originalName = resume.file.originalName,
                sizeBytes = resume.file.sizeBytes,
                contentType = resume.file.contentType,
                summaryStatus = ResumeSummaryStatus.PROCESSING,
                isDefault = resumeCount == 0L,
            ),
        ).id

        return resumeId
    }

    private fun lockMember(memberId: UUID) {
        // 보관함 행이 없으므로 회원 행을 잠가 같은 회원의 동시 등록을 직렬화한다.
        val member = memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId)
        if (member == null) throw CoreException(CoreErrorType.MEMBER_NOT_FOUND)
    }
}

private const val MAX_RESUME_COUNT = 10
