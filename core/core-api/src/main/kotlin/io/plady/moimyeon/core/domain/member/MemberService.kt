package io.plady.moimyeon.core.domain.member

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class MemberService(
    private val memberFinder: MemberFinder,
) {
    fun getMember(memberId: UUID): Member = memberFinder.getById(memberId)
}
