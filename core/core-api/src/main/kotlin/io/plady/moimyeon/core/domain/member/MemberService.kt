package io.plady.moimyeon.core.domain.member

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
        memberManager.changeNickname(memberId, Nickname(rawNickname))
    }
}
