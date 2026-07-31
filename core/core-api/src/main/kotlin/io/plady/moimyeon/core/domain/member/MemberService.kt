package io.plady.moimyeon.core.domain.member

import io.plady.moimyeon.core.support.error.CoreErrorType
import io.plady.moimyeon.core.support.error.requireBusiness
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class MemberService(
    private val memberFinder: MemberFinder,
    private val memberManager: MemberManager,
    private val nicknameGenerator: NicknameGenerator,
) {
    fun getMember(memberId: UUID): Member = memberFinder.getById(memberId)

    fun suggestNickname(): Nickname = nicknameGenerator.generateUnique()

    fun isNicknameAvailable(rawNickname: String): Boolean {
        return memberFinder.isNicknameAvailable(Nickname(rawNickname))
    }

    fun changeNickname(memberId: UUID, rawNickname: String) {
        val nickname = Nickname(rawNickname)
        memberFinder.getById(memberId)
        requireBusiness(memberFinder.isNicknameAvailableFor(memberId, nickname), CoreErrorType.NICKNAME_DUPLICATED)
        memberManager.changeNickname(memberId, nickname) // 동시 변경 레이스는 Manager 가 번역한다
    }
}
