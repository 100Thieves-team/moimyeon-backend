package io.plady.moimyeon.core.domain.profile

import io.plady.moimyeon.core.domain.member.MemberManager
import io.plady.moimyeon.core.domain.member.Nickname
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class ProfileUpdater(
    private val memberManager: MemberManager,
    private val profileManager: ProfileManager,
) {
    // 닉네임과 상세 프로필은 저장 위치가 다르지만 하나의 프로필 수정 행위에서는 한 커밋으로 처리한다.
    @Transactional
    fun update(memberId: UUID, nickname: Nickname, content: ProfileContent): UUID {
        memberManager.changeNickname(memberId, nickname)
        return profileManager.update(memberId, content)
    }
}
