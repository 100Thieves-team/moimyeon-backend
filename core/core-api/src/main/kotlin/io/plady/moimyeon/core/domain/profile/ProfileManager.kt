package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.domain.terms.TermsAgreementFinder
import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberProfileRepository
import io.plady.moimyeon.storage.db.core.MemberRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Component
class ProfileManager(
    private val memberRepository: MemberRepository,
    private val memberProfileRepository: MemberProfileRepository,
    private val profileInterestManager: ProfileInterestManager,
    private val termsAgreementFinder: TermsAgreementFinder,
) {
    @Transactional
    fun append(memberId: UUID, content: ProfileContent): UUID {
        // 최초 생성은 프로필 행이 아직 없어 잠글 대상이 없다 — 부모(member) 행을 잠가
        // 확인-후-저장 동시 생성을 직렬화한다. 두 번째 트랜잭션은 락 해제 후 아래
        // findForUpdateByMemberId 에서 먼저 커밋된 행을 보고 PROFILE_ALREADY_EXISTS 로 떨어진다.
        // 락 획득이 목적이라 member 개념의 Finder 로는 대체할 수 없어 Repository 를 직접 쓴다.
        requireFound(memberRepository.findForUpdateByIdAndDeletedAtIsNull(memberId), CoreErrorType.MEMBER_NOT_FOUND)
        requireBusiness(termsAgreementFinder.hasAgreedAllRequiredActive(memberId), CoreErrorType.TERMS_NOT_AGREED)

        val existing = memberProfileRepository.findForUpdateByMemberId(memberId)
        if (existing == null) {
            memberProfileRepository.save(ProfileMapper.toEntity(memberId, content))
        } else {
            requireBusiness(existing.isDeleted(), CoreErrorType.PROFILE_ALREADY_EXISTS)
            existing.active()
            existing.updateProfile(content.bio, content.meetingPreference, content.sigunguId)
        }
        replaceInterests(memberId, content)
        return memberId
    }

    @Transactional
    fun update(memberId: UUID, content: ProfileContent): UUID {
        val entity = requireFound(
            memberProfileRepository.findForUpdateByMemberId(memberId)?.takeIf { it.isActive() },
            CoreErrorType.PROFILE_NOT_FOUND,
        )
        entity.updateProfile(content.bio, content.meetingPreference, content.sigunguId)
        replaceInterests(memberId, content)
        return entity.memberId
    }

    private fun replaceInterests(memberId: UUID, content: ProfileContent) {
        profileInterestManager.replaceAll(
            memberId,
            content.interestCompanyIds,
            content.interestJobRoleIds,
            LocalDateTime.now(),
        )
    }
}
