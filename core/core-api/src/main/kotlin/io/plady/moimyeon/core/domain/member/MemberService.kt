package io.plady.moimyeon.core.domain.member

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class MemberService(
    private val memberFinder: MemberFinder,
    private val nicknameGenerator: NicknameGenerator,
) {
    fun getMember(memberId: UUID): Member = memberFinder.getById(memberId)

    fun getMembers(memberIds: Collection<UUID>): List<Member> = memberFinder.getAllByIds(memberIds)

    fun suggestNickname(): Nickname = nicknameGenerator.generateUnique()

    fun isNicknameAvailable(rawNickname: String): Boolean {
        return memberFinder.isNicknameAvailable(Nickname(rawNickname))
    }
}
