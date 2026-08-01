package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireFound
import io.plady.moimyeon.storage.db.core.MemberProfileEntity
import io.plady.moimyeon.storage.db.core.MemberProfileRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Component
class ProfileManager(
    private val memberProfileRepository: MemberProfileRepository,
    private val profileInterestManager: ProfileInterestManager,
) {
    // 가입 시 빈 프로필을 함께 만든다. 프로필은 회원당 항상 하나 존재하므로 이후 경로는 수정뿐이다.
    // 동시 호출은 uk_member_profile_member 가 막고, 그 상황은 가입이 두 번 커밋됐다는 뜻이라
    // 도메인 에러로 번역하지 않고 전파한다.
    @Transactional
    fun createEmpty(memberId: UUID): UUID {
        return memberProfileRepository.save(MemberProfileEntity(id = UUID.randomUUID(), memberId = memberId)).id
    }

    @Transactional
    fun update(memberId: UUID, content: ProfileContent): UUID {
        val entity = requireFound(
            memberProfileRepository.findForUpdateByMemberId(memberId)?.takeIf { it.isActive() },
            CoreErrorType.PROFILE_NOT_FOUND,
        )
        entity.updateProfile(content.bio, content.meetingPreference, content.sigunguId)
        profileInterestManager.replaceAll(
            entity.id,
            content.interestCompanyIds,
            content.interestJobRoleIds,
            LocalDateTime.now(),
        )
        return entity.memberId
    }
}
