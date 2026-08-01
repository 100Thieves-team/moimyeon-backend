package io.plady.moimyeon.core.domain.resume

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberRepository
import io.plady.moimyeon.storage.db.core.ResumeRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Component
class ResumeManager(
    private val memberRepository: MemberRepository,
    private val resumeRepository: ResumeRepository,
) {
    @Transactional
    fun makeDefault(memberId: UUID, resumeId: UUID) {
        lockMember(memberId)
        val selected = getSelectableResume(memberId, resumeId)
        if (selected.isDefault) return

        val current = checkNotNull(
            resumeRepository.findByMemberIdAndIsDefaultTrueAndArchivedAtIsNullAndDeletedAtIsNull(memberId),
        ) { "이력서가 있는 보관함에는 기본 이력서가 하나 있어야 합니다." }
        current.releaseDefault()
        // 회원별 기본 이력서 유니크를 해제한 뒤 새 기본을 지정한다.
        resumeRepository.flush()
        selected.makeDefault()
    }

    @Transactional
    fun hide(memberId: UUID, resumeId: UUID, hiddenAt: LocalDateTime) {
        lockMember(memberId)
        val resume = getSelectableResume(memberId, resumeId)
        requireBusiness(resume.canHide(), CoreErrorType.DEFAULT_RESUME_CANNOT_BE_HIDDEN)
        resume.hide(hiddenAt)
    }

    private fun lockMember(memberId: UUID) {
        // 보관함 행을 따로 두지 않으므로 회원 행이 같은 회원의 등록·기본 변경·숨김을 직렬화한다.
        requireFound(memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId), CoreErrorType.MEMBER_NOT_FOUND)
    }

    private fun getSelectableResume(memberId: UUID, resumeId: UUID) = requireFound(
        resumeRepository.findByIdAndMemberIdAndArchivedAtIsNullAndDeletedAtIsNull(resumeId, memberId),
        CoreErrorType.RESUME_NOT_FOUND,
    )
}
